package cn.wwl.orimod.devtools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Development-only bridge. Its source set and mod are absent from normal runs and release jars. */
@Mod(value = "orimod_devtools", dist = Dist.CLIENT)
public final class DevelopmentBridge {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final String token = UUID.randomUUID().toString();
    private final Map<KeyMapping, Integer> heldKeys = new HashMap<>();
    private final ArrayList<Double> frameTimes = new ArrayList<>();
    private final FrameRecorder video = new FrameRecorder();
    private long ticks;
    private long frames;
    private long previousFrame;
    private boolean recordingFrames;
    private volatile int completedReloads;
    private volatile String reloadError = "";
    private volatile boolean recordingMotion;
    private final java.util.concurrent.ConcurrentLinkedQueue<Map<String, Object>> motion = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final ArrayList<JsonObject> inputScript = new ArrayList<>();
    private long inputScriptStart;
    private String inputScriptError = "";
    private Path directory;

    public DevelopmentBridge(IEventBus modBus) {
        if (!Boolean.getBoolean("orimod.devtools.enabled")) {
            throw new IllegalStateException("Development tools require its explicit run configuration");
        }
        modBus.addListener(this::setup);
        CharacterPreview.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
        NeoForge.EVENT_BUS.addListener(this::playerTick);
    }

    private void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                var mc = Minecraft.getInstance();
                mc.options.pauseOnLostFocus = false;
                directory = mc.gameDirectory.toPath().resolve("devtools");
                Files.createDirectories(directory);
                var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
                server.createContext("/state", this::stateRequest);
                server.createContext("/action", this::actionRequest);
                server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                server.start();
                Files.writeString(directory.resolve("connection.json"), JSON.toJson(Map.of(
                        "url", "http://127.0.0.1:" + server.getAddress().getPort(),
                        "token", token, "pid", ProcessHandle.current().pid())));
                Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0), "OriMod development bridge shutdown"));
            } catch (IOException error) {
                throw new IllegalStateException("Unable to start OriMod development bridge", error);
            }
        });
    }

    private boolean authorize(HttpExchange request, String method) throws IOException {
        if (!method.equals(request.getRequestMethod())) {
            reply(request, 405, Map.of("error", "METHOD_NOT_ALLOWED"));
            return false;
        }
        if (!token.equals(request.getRequestHeaders().getFirst("X-Ori-Devtools"))) {
            reply(request, 403, Map.of("error", "UNAUTHORIZED"));
            return false;
        }
        return true;
    }

    private void stateRequest(HttpExchange request) throws IOException {
        if (!authorize(request, "GET")) return;
        try {
            reply(request, 200, Minecraft.getInstance().submit(this::state).get(10, TimeUnit.SECONDS));
        } catch (Exception error) {
            reply(request, 503, Map.of("error", error.toString()));
        }
    }

    private void actionRequest(HttpExchange request) throws IOException {
        if (!authorize(request, "POST")) return;
        var bytes = request.getRequestBody().readNBytes(16_385);
        if (bytes.length > 16_384) {
            reply(request, 413, Map.of("error", "REQUEST_TOO_LARGE"));
            return;
        }
        try {
            var action = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = Minecraft.getInstance().submit(() -> {
                try {
                    return action(action);
                } catch (Exception error) {
                    throw new java.util.concurrent.CompletionException(error);
                }
            }).get(45, TimeUnit.SECONDS);
            reply(request, 200, result);
        } catch (Exception error) {
            reply(request, 500, Map.of("error", error.toString(),
                    "note", "Inspect state before retrying; an observation timeout does not cancel an action."));
        }
    }

    private Map<String, Object> action(JsonObject action) throws Exception {
        var mc = Minecraft.getInstance();
        String operation = action.get("op").getAsString();
        switch (operation) {
            case "create_world" -> {
                require(mc.getOverlay() == null, "Wait for initial resource loading to finish");
                require(mc.level == null, "Leave the current world first");
                String name = worldName(action);
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(name)), "World already exists");
                var settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings,
                        new WorldOptions(action.get("seed").getAsLong(), true, false),
                        WorldPresets::createNormalWorldDimensions, new TitleScreen());
            }
            case "open_world" -> {
                require(mc.getOverlay() == null, "Wait for initial resource loading to finish");
                require(mc.level == null, "Leave the current world first");
                String name = worldName(action);
                require(Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(name).resolve("level.dat")), "World missing");
                mc.createWorldOpenFlows().openWorld(name, () -> mc.setScreen(new TitleScreen()));
            }
            case "leave_world" -> {
                releaseKeys();
                if (mc.level != null) mc.level.disconnect();
                mc.disconnect();
                mc.setScreen(new TitleScreen());
            }
            case "command" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var server = mc.getSingleplayerServer();
                var playerId = mc.player.getUUID();
                String command = action.get("command").getAsString();
                server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    require(player != null, "Server player missing");
                    server.getCommands().performPrefixedCommand(
                            player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
                }).get(10, TimeUnit.SECONDS);
            }
            case "inspect_block" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var playerId = mc.player.getUUID();
                var pos = new net.minecraft.core.BlockPos(action.get("x").getAsInt(), action.get("y").getAsInt(), action.get("z").getAsInt());
                return mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    var block = player.level().getBlockState(pos);
                    return Map.<String, Object>of("block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(),
                            "state", block.toString(), "fluid", block.getFluidState().getType().toString());
                }).get(5, TimeUnit.SECONDS);
            }
            case "key" -> {
                require(mc.player != null, "Player required");
                String name = action.get("name").getAsString();
                int duration = action.get("ticks").getAsInt();
                require(duration >= 1 && duration <= 2400, "Invalid hold duration");
                KeyMapping key = java.util.Arrays.stream(mc.options.keyMappings)
                        .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
                key.setDown(true);
                KeyMapping.click(key.getKey());
                heldKeys.put(key, duration);
            }
            case "release_keys" -> releaseKeys();
            case "input_script" -> {
                require(mc.player != null && mc.screen == null, "Gameplay required");
                releaseKeys();
                inputScript.clear();
                inputScriptError = "";
                for (var step : action.getAsJsonArray("steps")) {
                    var entry = step.getAsJsonObject();
                    int at = entry.get("at").getAsInt();
                    require(at >= 0 && at <= 2400, "Invalid input tick");
                    if (entry.has("key")) {
                        require(java.util.Arrays.stream(mc.options.keyMappings).anyMatch(key -> key.getName().equals(entry.get("key").getAsString())), "Unknown input key");
                    } else {
                        require(entry.has("action") && java.util.Set.of("command", "select_slot", "open_inventory", "open_pause", "close_screen", "toggle_form", "look")
                                .contains(entry.getAsJsonObject("action").get("op").getAsString()), "Unsupported scheduled action");
                    }
                    inputScript.add(entry);
                }
                inputScript.sort(java.util.Comparator.comparingInt(step -> step.get("at").getAsInt()));
                inputScriptStart = ticks + 1;
            }
            case "record_motion" -> { motion.clear(); recordingMotion = true; }
            case "save_motion" -> {
                recordingMotion = false;
                Path file = directory.resolve(fileName(action, ".json"));
                require(!Files.exists(file), "Motion recording already exists");
                Files.writeString(file, JSON.toJson(Map.of("samples", new ArrayList<>(motion), "state", state())));
                return Map.of("path", file.toAbsolutePath().toString(), "samples", motion.size());
            }
            case "toggle_form" -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new cn.wwl.orimod.network.ToggleSpiritPayload());
            case "grant_dash" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    player.setData(cn.wwl.orimod.spirit.SpiritData.ABILITIES, player.getData(cn.wwl.orimod.spirit.SpiritData.ABILITIES) | 1);
                }).get(5, TimeUnit.SECONDS);
            }
            case "grant_bash", "grant_air_movement" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    int mask = operation.equals("grant_bash") ? 2 : 4 | 8 | 16;
                    player.setData(cn.wwl.orimod.spirit.SpiritData.ABILITIES, player.getData(cn.wwl.orimod.spirit.SpiritData.ABILITIES) | mask);
                }).get(5, TimeUnit.SECONDS);
            }
            case "nearby_entities" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                return mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var entities = player.level().getEntities(player, player.getBoundingBox().inflate(24), entity -> true);
                    return Map.<String, Object>of("entities", entities.stream().map(entity -> Map.of("id", entity.getId(), "uuid", entity.getUUID().toString(),
                            "type", entity.getType().toString(), "alive", entity.isAlive(), "position", new double[]{entity.getX(), entity.getY(), entity.getZ()},
                            "velocity", new double[]{entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z},
                            "health", entity instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : -1,
                            "noGravity", entity.isNoGravity(), "noAi", entity instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi())).toList());
                }).get(5, TimeUnit.SECONDS);
            }
            case "progress_state" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                var client = progressState(mc.player);
                var server = mc.getSingleplayerServer().submit(() -> progressState(mc.getSingleplayerServer().getPlayerList().getPlayer(id))).get(5, TimeUnit.SECONDS);
                return Map.of("client", client, "server", server);
            }
            case "grant_light", "set_energy" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                int amount = action.get("amount").getAsInt();
                require(amount >= 0 && amount <= 10000, "Amount out of range");
                mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    if (operation.equals("grant_light")) cn.wwl.orimod.progression.SpiritProgression.grantLight(player, amount);
                    else {
                        player.setData(cn.wwl.orimod.spirit.SpiritData.ENERGY, (float)amount);
                        player.getData(cn.wwl.orimod.spirit.SpiritData.ENERGY_STATE).lastSpent = player.level().getGameTime();
                    }
                }).get(5, TimeUnit.SECONDS);
            }
            case "progress_action" -> {
                require(mc.player != null, "Player required");
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new cn.wwl.orimod.network.ProgressActionPayload(
                        action.get("action").getAsString(), action.get("target").getAsString()));
            }
            case "select_slot" -> {
                require(mc.player != null, "Player required");
                int slot = action.get("slot").getAsInt();
                require(slot >= 0 && slot < 9, "Invalid hotbar slot");
                mc.player.getInventory().selected = slot;
            }
            case "attack_entity" -> {
                require(mc.player != null && mc.gameMode != null, "Player required");
                var target = mc.level.getEntity(action.get("id").getAsInt());
                require(target != null, "Entity missing");
                mc.gameMode.attack(mc.player, target); mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            case "gui_state" -> {
                require(mc.screen != null, "Screen required");
                var widgets = new ArrayList<Map<String, Object>>();
                for (var child : mc.screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                        var info = new LinkedHashMap<String, Object>();
                        info.put("type", widget.getClass().getSimpleName()); info.put("text", widget.getMessage().getString());
                        info.put("x", widget.getX()); info.put("y", widget.getY()); info.put("width", widget.getWidth()); info.put("height", widget.getHeight());
                        info.put("active", widget.active); info.put("visible", widget.visible); info.put("focused", widget.isFocused());
                        if (widget instanceof net.minecraft.client.gui.components.ObjectSelectionList<?> list) {
                            info.put("entries", list.children().stream().map(entry -> entry.getNarration().getString()).toList());
                            info.put("selected", list.getSelected() == null ? "" : list.getSelected().getNarration().getString());
                            info.put("scroll", list.getScrollAmount());
                        }
                        widgets.add(info);
                    }
                }
                return Map.of("screen", mc.screen.getClass().getSimpleName(), "width", mc.screen.width, "height", mc.screen.height,
                        "widgets", widgets, "reply", cn.wwl.orimod.network.ProgressReplyPayload.latest.getString());
            }
            case "gui_click" -> {
                require(mc.screen != null, "Screen required");
                double x = action.get("x").getAsDouble(), y = action.get("y").getAsDouble();
                mc.screen.mouseClicked(x, y, 0); mc.screen.mouseReleased(x, y, 0);
            }
            case "gui_scroll" -> {
                require(mc.screen != null, "Screen required");
                mc.screen.mouseScrolled(action.get("x").getAsDouble(), action.get("y").getAsDouble(), 0, action.get("amount").getAsDouble());
            }
            case "gui_key" -> {
                require(mc.screen != null, "Screen required");
                int key = action.get("key").getAsInt();
                mc.screen.keyPressed(key, 0, action.has("modifiers") ? action.get("modifiers").getAsInt() : 0);
                if (mc.screen != null) mc.screen.keyReleased(key, 0, 0);
            }
            case "language" -> {
                String language = action.get("code").getAsString();
                require(mc.getLanguageManager().getLanguage(language) != null, "Unknown language");
                mc.getLanguageManager().setSelected(language); mc.options.languageCode = language; mc.options.save();
                mc.reloadResourcePacks();
            }
            case "gui_scale" -> {
                int scale = action.get("scale").getAsInt();
                require(scale >= 0 && scale <= 6, "Invalid GUI scale");
                mc.options.guiScale().set(scale); mc.resizeDisplay();
            }
            case "open_inventory" -> { require(mc.player != null, "Player required"); mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); }
            case "open_pause" -> mc.pauseGame(false);
            case "respawn" -> { require(mc.player != null && !mc.player.isAlive(), "Dead player required"); mc.player.respawn(); mc.setScreen(null); }
            case "use_item" -> {
                require(mc.player != null && mc.gameMode != null && mc.screen == null, "Gameplay required");
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            case "spirit_state" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                var serverState = mc.getSingleplayerServer().submit(() -> spiritState(
                        mc.getSingleplayerServer().getPlayerList().getPlayer(id))).get(5, TimeUnit.SECONDS);
                return Map.of("client", spiritState(mc.player), "server", serverState);
            }
            case "look" -> {
                require(mc.player != null, "Player required");
                float yaw = action.get("yaw").getAsFloat();
                float pitch = action.get("pitch").getAsFloat();
                require(Float.isFinite(yaw) && Float.isFinite(pitch), "Non-finite angle");
                mc.player.setYRot(yaw);
                mc.player.setXRot(Math.clamp(pitch, -90, 90));
                mc.player.setYHeadRot(yaw);
            }
            case "camera" -> mc.options.setCameraType(CameraType.valueOf(action.get("mode").getAsString()));
            case "close_screen" -> { if (!action.has("keepKeys") || !action.get("keepKeys").getAsBoolean()) releaseKeys(); mc.setScreen(null); }
            case "screenshot" -> {
                String name = fileName(action, ".png");
                Path file = directory.resolve(name);
                require(!Files.exists(file), "Screenshot already exists");
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(file);
                }
                return Map.of("path", file.toAbsolutePath().toString(), "frame", frames, "state", state());
            }
            case "resize" -> {
                int width = action.get("width").getAsInt();
                int height = action.get("height").getAsInt();
                require(width >= 640 && width <= 3840 && height >= 480 && height <= 2160, "Invalid size");
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height);
            }
            case "reload_resources" -> mc.reloadResourcePacks().whenComplete((result, error) -> {
                if (error == null) completedReloads++;
                else reloadError = error.toString();
            });
            case "shaders" -> {
                net.irisshaders.iris.api.v0.IrisApi.getInstance().getConfig()
                        .setShadersEnabledAndApply(action.get("enabled").getAsBoolean());
                com.lowdragmc.photon.client.compat.iris.IrisCompat.invalidate();
            }
            case "render_diagnostics" -> {
                return Map.of("photon", com.lowdragmc.photon.client.compat.iris.IrisDiagnostics.report(
                        com.lowdragmc.photon.client.compat.iris.IrisCompat.diagnosticsTarget()));
            }
            case "preview_effect" -> {
                return SpiritMotePreview.start(directory, new net.minecraft.core.BlockPos(
                        action.get("x").getAsInt(), action.get("y").getAsInt(), action.get("z").getAsInt()));
            }
            case "stop_preview_effect" -> SpiritMotePreview.stop();
            case "settings" -> {
                if (action.has("renderDistance")) mc.options.renderDistance().set(Math.clamp(action.get("renderDistance").getAsInt(), 2, 32));
                if (action.has("vsync")) mc.options.enableVsync().set(action.get("vsync").getAsBoolean());
                if (action.has("fpsLimit")) mc.options.framerateLimit().set(Math.clamp(action.get("fpsLimit").getAsInt(), 10, 260));
                if (action.has("hideGui")) mc.options.hideGui = action.get("hideGui").getAsBoolean();
                mc.options.save();
            }
            case "record_frames" -> {
                frameTimes.clear();
                previousFrame = 0;
                recordingFrames = true;
            }
            case "record_video" -> video.start(directory.resolve(fileName(action, "")),
                    action.get("fps").getAsInt(), action.get("seconds").getAsInt());
            case "save_video" -> { return video.finish(); }
            case "save_frames" -> {
                recordingFrames = false;
                Path file = directory.resolve(fileName(action, ".json"));
                require(!Files.exists(file), "Frame recording already exists");
                Files.writeString(file, JSON.toJson(Map.of("frameTimeMs", frameTimes, "state", state())));
                return Map.of("path", file.toAbsolutePath().toString(), "samples", frameTimes.size());
            }
            case "quit" -> mc.stop();
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }
        return Map.of("accepted", operation, "state", state());
    }

    private static String worldName(JsonObject action) {
        String name = action.get("name").getAsString();
        require(name.matches("orimod_dev_[a-zA-Z0-9_-]{1,48}"), "Invalid test-world name");
        return name;
    }

    private static String fileName(JsonObject action, String suffix) {
        String name = action.get("name").getAsString();
        require(name.matches("[a-zA-Z0-9_-]{1,80}"), "Invalid evidence filename");
        return name + suffix;
    }

    private void tick(ClientTickEvent.Post event) {
        ticks++;
        SpiritMotePreview.tick();
        if (Minecraft.getInstance().player == null) { releaseKeys(); return; }
        heldKeys.replaceAll((key, duration) -> duration - 1);
        heldKeys.entrySet().removeIf(entry -> {
            if (entry.getValue() > 0) return false;
            entry.getKey().setDown(false);
            return true;
        });
        while (!inputScript.isEmpty() && inputScript.getFirst().get("at").getAsInt() <= ticks - inputScriptStart) {
            var step = inputScript.removeFirst();
            if (step.has("action")) {
                try { action(step.getAsJsonObject("action")); }
                catch (Exception error) { inputScriptError = error.toString(); inputScript.clear(); }
                continue;
            }
            var key = java.util.Arrays.stream(Minecraft.getInstance().options.keyMappings)
                    .filter(candidate -> candidate.getName().equals(step.get("key").getAsString())).findFirst().orElseThrow();
            boolean down = step.get("down").getAsBoolean();
            key.setDown(down);
            if (down) { KeyMapping.click(key.getKey()); heldKeys.put(key, 2400); }
            else heldKeys.remove(key);
        }
    }

    private void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!recordingMotion || motion.size() > 10_000) return;
        var player = event.getEntity();
        motion.add(Map.of("side", player.level().isClientSide ? "client" : "server", "tick", player.level().getGameTime(),
                "sampleNanos", System.nanoTime(), "state", spiritState(player)));
    }

    private void frame(RenderFrameEvent.Post event) {
        frames++;
        long now = System.nanoTime();
        video.frame(now);
        if (recordingFrames && previousFrame != 0 && frameTimes.size() < 300_000) {
            frameTimes.add((now - previousFrame) / 1_000_000.0);
        }
        previousFrame = now;
    }

    private void releaseKeys() {
        inputScript.clear();
        heldKeys.keySet().forEach(key -> key.setDown(false));
        heldKeys.clear();
    }

    private Map<String, Object> state() {
        var mc = Minecraft.getInstance();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("tick", ticks);
        state.put("inputScriptError", inputScriptError);
        state.put("frame", frames);
        state.put("screen", mc.screen == null ? "gameplay" : mc.screen.getClass().getSimpleName());
        state.put("windowActive", mc.isWindowActive());
        state.put("fps", mc.getFps());
        state.put("renderDistance", mc.options.renderDistance().get());
        state.put("vsync", mc.options.enableVsync().get());
        state.put("fpsLimit", mc.options.framerateLimit().get());
        state.put("characterPreview", CharacterPreview.state());
        state.put("effectPreview", SpiritMotePreview.state());
        state.put("shaders", Map.of("enabled", net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse(),
                "pack", net.irisshaders.iris.Iris.getCurrentPackName()));
        state.put("resourceReloads", completedReloads);
        state.put("video", video.state());
        state.put("reloadError", reloadError);
        state.put("overlay", mc.getOverlay() == null ? "none" : mc.getOverlay().getClass().getSimpleName());
        state.put("framebuffer", Map.of("width", mc.getMainRenderTarget().width, "height", mc.getMainRenderTarget().height));
        state.put("camera", mc.options.getCameraType().name());
        state.put("mods", ModList.get().getMods().stream().map(mod -> mod.getModId() + "@" + mod.getVersion()).toList());
        if (mc.player != null && mc.level != null) {
            var player = mc.player;
            state.put("player", Map.of("position", new double[]{player.getX(), player.getY(), player.getZ()},
                    "velocity", new double[]{player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z},
                    "yaw", player.getYRot(), "pitch", player.getXRot(), "onGround", player.onGround(),
                    "health", player.getHealth(), "pose", player.getPose().name(),
                    "dimension", mc.level.dimension().location().toString()));
        }
        return state;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Map<String, Object> spiritState(net.minecraft.world.entity.player.Player player) {
        var state = new LinkedHashMap<String, Object>(Map.of("awakened", cn.wwl.orimod.spirit.SpiritData.isAwakened(player),
                "form", cn.wwl.orimod.spirit.SpiritData.isSpirit(player),
                "height", player.getBbHeight(), "width", player.getBbWidth(), "eyeHeight", player.getEyeHeight(),
                "health", player.getHealth(), "fallDistance", player.fallDistance,
                "selectedItem", player.getMainHandItem().getItem().toString(),
                "position", new double[]{player.getX(), player.getY(), player.getZ()}));
        var movement = player.getData(cn.wwl.orimod.spirit.SpiritData.MOVEMENT);
        var combat = player.getData(cn.wwl.orimod.spirit.SpiritData.COMBAT);
        var view = player.getData(cn.wwl.orimod.spirit.SpiritData.COMBAT_VIEW);
        state.put("combat", Map.ofEntries(Map.entry("view", view), Map.entry("mode", combat.mode), Map.entry("stage", combat.stage),
                Map.entry("startedAt", combat.startedAt), Map.entry("buffered", combat.buffered), Map.entry("bladeStarts", combat.bladeStarts),
                Map.entry("bladeHits", combat.bladeHits), Map.entry("bowShots", combat.bowShots), Map.entry("cancellations", combat.cancellations),
                Map.entry("lastCancel", combat.lastCancel), Map.entry("usingItem", player.isUsingItem()),
                Map.entry("useTicks", player.getTicksUsingItem()), Map.entry("energy", cn.wwl.orimod.progression.SpiritProgression.energy(player))));
        state.put("movement", Map.ofEntries(Map.entry("mode", movement.mode), Map.entry("activeTicks", movement.activeTicks),
                Map.entry("airJumpSpent", movement.airJumpSpent), Map.entry("airDashSpent", movement.airDashSpent),
                Map.entry("inputSequence", movement.sequence), Map.entry("inputActive", movement.active),
                Map.entry("lastAction", movement.lastAction), Map.entry("wallDirection", movement.wallDirection),
                Map.entry("jumps", movement.acceptedJumps), Map.entry("wallJumps", movement.acceptedWallJumps),
                Map.entry("dashes", movement.acceptedDashes), Map.entry("rejectedDashes", movement.rejectedDashes),
                Map.entry("dashUnlocked", cn.wwl.orimod.spirit.SpiritData.hasDash(player)),
                Map.entry("gameTime", player.level().getGameTime()), Map.entry("jumpUntil", movement.jumpUntil),
                Map.entry("jumpPressedAt", movement.jumpPressedAt),
                Map.entry("groundedAt", movement.groundedAt), Map.entry("groundJumpAvailable", movement.groundJumpAvailable),
                Map.entry("lastWallRefill", movement.lastWallRefill),
                Map.entry("bashUnlocked", cn.wwl.orimod.spirit.SpiritData.hasBash(player)),
                Map.entry("bashes", movement.acceptedBashes), Map.entry("cancelledBashes", movement.cancelledBashes),
                Map.entry("grapples", movement.acceptedGrapples), Map.entry("completedGrapples", movement.completedGrapples),
                Map.entry("cancelledGrapples", movement.cancelledGrapples), Map.entry("stomps", movement.acceptedStomps),
                Map.entry("stompImpacts", movement.stompImpacts), Map.entry("glides", movement.startedGlides),
                Map.entry("observedDy", movement.observedDy),
                Map.entry("targetId", movement.bash == null ? movement.targetId : movement.bash.target.entity() == null ? -1 : movement.bash.target.entity().getId()),
                Map.entry("targetPosition", movement.bash == null ? movement.targetPosition : movement.bash.targetPosition),
                Map.entry("grounded", cn.wwl.orimod.movement.SpiritMovement.grounded(player)),
                Map.entry("velocity", new double[]{player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z})));
        return state;
    }

    private static void reply(HttpExchange request, int status, Object data) throws IOException {
        byte[] body = JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.sendResponseHeaders(status, body.length);
        try (var response = request.getResponseBody()) { response.write(body); }
    }

    private static Map<String, Object> progressState(net.minecraft.world.entity.player.Player player) {
        return Map.of("progress", player.getData(cn.wwl.orimod.spirit.SpiritData.PROGRESS),
                "abilities", player.getData(cn.wwl.orimod.spirit.SpiritData.ABILITIES),
                "energy", cn.wwl.orimod.progression.SpiritProgression.energy(player),
                "maxEnergy", cn.wwl.orimod.progression.SpiritProgression.maxEnergy(player),
                "slots", cn.wwl.orimod.progression.SpiritProgression.slots(player),
                "gameTime", player.level().getGameTime(), "experienceLevel", player.experienceLevel,
                "inventory", player.getInventory().items.stream().filter(stack -> !stack.isEmpty()).map(stack -> Map.of(
                        "item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "count", stack.getCount())).toList());
    }
}
