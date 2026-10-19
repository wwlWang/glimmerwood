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
    private boolean recordingPose;
    private final ArrayList<Map<String, Object>> poses = new ArrayList<>();
    private volatile int completedReloads;
    private volatile String reloadError = "";
    private volatile boolean recordingMotion;
    private final java.util.concurrent.ConcurrentLinkedQueue<Map<String, Object>> motion = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final ArrayList<JsonObject> inputScript = new ArrayList<>();
    private long inputScriptStart;
    private String inputScriptError = "";
    private volatile Map<String, Object> lifecycleSnapshot = Map.of("frozen", 0, "challenges", 0, "regionTickets", 0,
            "locator", Map.of("searches", 0, "cooldowns", 0, "tickets", 0));
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
        NeoForge.EVENT_BUS.addListener(this::serverTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, this::serverStopped);
        NeoForge.EVENT_BUS.addListener(ReviewCamera::tick);
        NeoForge.EVENT_BUS.addListener(LayerPreview::tick);
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
                boolean hardcore = action.has("hardcore") && action.get("hardcore").getAsBoolean();
                var settings = new LevelSettings(name, hardcore ? GameType.SURVIVAL : GameType.CREATIVE, hardcore, hardcore ? Difficulty.HARD : Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings,
                        new WorldOptions(action.get("seed").getAsLong(), !action.has("structures") || action.get("structures").getAsBoolean(), false),
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
                return server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    require(player != null, "Server player missing");
                    require(server.getCommands().getDispatcher().getRoot().getChild(command.split(" ", 2)[0]) != null,
                            "Command is unavailable in the integrated server");
                    var result = new java.util.concurrent.atomic.AtomicInteger();
                    var successful = new java.util.concurrent.atomic.AtomicBoolean();
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4)
                            .withSuppressedOutput().withCallback((success, value) -> { successful.set(success); result.set(value); }), command);
                    return Map.<String, Object>of("success", successful.get(), "result", result.get());
                }).get(10, TimeUnit.SECONDS);
            }
            case "save_world" -> {
                require(mc.getSingleplayerServer() != null && mc.isPaused(), "Pause the single-player world before taking a snapshot");
                var server = mc.getSingleplayerServer();
                return server.submit(() -> Map.<String, Object>of("saved", server.saveEverything(true, true, true),
                        "serverTick", server.getTickCount())).get(35, TimeUnit.SECONDS);
            }
            case "inspect_block" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var playerId = mc.player.getUUID();
                var pos = new net.minecraft.core.BlockPos(action.get("x").getAsInt(), action.get("y").getAsInt(), action.get("z").getAsInt());
                return mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    var block = player.level().getBlockState(pos);
                    return Map.<String, Object>of("block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(),
                            "state", block.toString(), "fluid", block.getFluidState().getType().toString(),
                            "fullBlock", block.isCollisionShapeFullBlock(player.level(), pos),
                            "collisionEmpty", block.getCollisionShape(player.level(), pos).isEmpty(),
                            "shape", block.getCollisionShape(player.level(), pos).toAabbs().stream().map(Object::toString).toList(),
                            "foliage", block.is(net.minecraft.tags.BlockTags.LEAVES) || block.is(net.minecraft.tags.BlockTags.LOGS));
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
            case "focus_game" -> GLFW.glfwFocusWindow(mc.getWindow().getWindow());
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
                        require(entry.has("action") && java.util.Set.of("command", "select_slot", "open_inventory", "open_pause", "close_screen", "toggle_form", "look", "server_hitch")
                                .contains(entry.getAsJsonObject("action").get("op").getAsString()), "Unsupported scheduled action");
                    }
                    inputScript.add(entry);
                }
                inputScript.sort(java.util.Comparator.comparingInt(step -> step.get("at").getAsInt()));
                inputScriptStart = ticks + 1;
            }
            case "record_motion" -> { motion.clear(); recordingMotion = true; }
            case "record_pose" -> { poses.clear(); recordingPose = true; }
            case "save_pose" -> {
                recordingPose = false;
                Path file = directory.resolve(fileName(action, ".json"));
                require(!Files.exists(file), "Pose recording already exists");
                Files.writeString(file, JSON.toJson(Map.of("samples", poses, "state", state())));
                return Map.of("path", file.toAbsolutePath().toString(), "samples", poses.size());
            }
            case "server_hitch" -> {
                require(mc.getSingleplayerServer() != null, "Single-player world required");
                int milliseconds = action.get("milliseconds").getAsInt();
                require(milliseconds >= 1 && milliseconds <= 1500, "Hitch must be between 1 and 1500 ms");
                mc.getSingleplayerServer().execute(() -> {
                    try { Thread.sleep(milliseconds); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                });
            }
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
                            "noGravity", entity.isNoGravity(), "noAi", entity instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi(),
                            "leashHolder", entity instanceof net.minecraft.world.entity.Mob leashed && leashed.getLeashHolder() != null ? leashed.getLeashHolder().getId() : -1)).toList());
                }).get(5, TimeUnit.SECONDS);
            }
            case "progress_state" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                var client = progressState(mc.player);
                var server = mc.getSingleplayerServer().submit(() -> progressState(mc.getSingleplayerServer().getPlayerList().getPlayer(id))).get(5, TimeUnit.SECONDS);
                return Map.of("client", client, "server", server);
            }
            case "use_block" -> {
                require(mc.player != null && mc.gameMode != null, "Player required");
                var pos = new net.minecraft.core.BlockPos(action.get("x").getAsInt(), action.get("y").getAsInt(), action.get("z").getAsInt());
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
                mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            }
            case "ruin_state" -> {
                require(mc.player != null && mc.getSingleplayerServer() != null, "Single-player world required");
                var id = mc.player.getUUID();
                var serverState = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var result = new LinkedHashMap<String, Object>();
                    result.put("hardcore", player.server.getWorldData().isHardcore());
                    result.put("gameMode", player.gameMode.getGameModeForPlayer().getName());
                    result.put("search", cn.wwl.orimod.world.RuinLocator.debug(player));
                    var session = cn.wwl.orimod.world.SpiritChallenge.session(player);
                    result.put("active", session != null);
                    if (session != null) {
                        result.put("kind", session.kind.id); result.put("key", session.key());
                        result.put("checkpoint", session.checkpoint); result.put("baseline", session.baseline);
                        result.put("elapsed", player.level().getGameTime() - session.started);
                        result.put("target", session.target == null ? "" : session.target.toString());
                        result.put("origin", new int[]{session.origin.getX(), session.origin.getY(), session.origin.getZ()});
                        result.put("rotation", session.placement.rotation().name());
                    }
                    result.put("recovery", player.getData(cn.wwl.orimod.spirit.SpiritData.CHALLENGE_RECOVERY));
                    var starts = new ArrayList<Map<String, Object>>();
                    var chunkPos = player.chunkPosition();
                    for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                        var chunk = player.serverLevel().getChunkSource().getChunkNow(chunkPos.x + dx, chunkPos.z + dz);
                        if (chunk == null) continue;
                        for (var start : chunk.getAllStarts().values()) {
                            if (!start.isValid() || !(start.getStructure() instanceof cn.wwl.orimod.world.RuinStructure ruin)) continue;
                            for (var piece : start.getPieces()) if (piece instanceof cn.wwl.orimod.world.RuinPiece part) {
                                var origin = part.origin();
                                starts.add(Map.of("kind", ruin.kind.id, "origin", new int[]{origin.getX(), origin.getY(), origin.getZ()},
                                        "rotation", part.getRotation().name(), "bounds", part.getBoundingBox().toString()));
                            }
                        }
                    }
                    result.put("naturalStarts", starts);
                    return result;
                }).get(5, TimeUnit.SECONDS);
                var guide = cn.wwl.orimod.network.RuinGuidePayload.current;
                serverState.put("guide", guide == null ? "none" : Map.of("dimension", guide.dimension(), "kind", guide.kind(), "entry", new int[]{guide.entry().getX(), guide.entry().getY(), guide.entry().getZ()}));
                serverState.put("reply", cn.wwl.orimod.network.ProgressReplyPayload.latest.getString());
                serverState.put("replyRevision", cn.wwl.orimod.network.ProgressReplyPayload.revision);
                return serverState;
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
                        "widgets", widgets, "narration", mc.screen.getNarrationMessage().getString(), "reply", cn.wwl.orimod.network.ProgressReplyPayload.latest.getString());
            }
            case "gui_click" -> {
                require(mc.screen != null, "Screen required");
                double x = action.get("x").getAsDouble(), y = action.get("y").getAsDouble();
                mc.screen.mouseClicked(x, y, 0);
                if (mc.screen != null) mc.screen.mouseReleased(x, y, 0);
            }
            case "gui_scroll" -> {
                require(mc.screen != null, "Screen required");
                mc.screen.mouseScrolled(action.get("x").getAsDouble(), action.get("y").getAsDouble(), 0, action.get("amount").getAsDouble());
            }
            case "container_state" -> {
                require(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "Container screen required");
                var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen;
                return Map.of("screen", screen.getClass().getSimpleName(), "slots", screen.getMenu().slots.stream().map(slot -> Map.of(
                        "index", slot.index, "x", screen.getGuiLeft() + slot.x, "y", screen.getGuiTop() + slot.y,
                        "item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString(),
                        "count", slot.getItem().getCount())).toList(),
                        "carried", screen.getMenu().getCarried().toString());
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
            case "item_placement" -> {
                require(mc.player != null, "Player required");
                return cn.wwl.orimod.client.render.SpiritHeldItems.diagnostics(mc.player);
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
            case "camera" -> { ReviewCamera.stop(); mc.options.setCameraType(CameraType.valueOf(action.get("mode").getAsString())); }
            case "review_camera" -> {
                if (!action.get("enabled").getAsBoolean()) ReviewCamera.stop();
                else {
                    double x=action.get("x").getAsDouble(), y=action.get("y").getAsDouble(), z=action.get("z").getAsDouble();
                    double height=action.has("targetHeight") ? action.get("targetHeight").getAsDouble() : .65;
                    require(Double.isFinite(x+y+z+height) && x*x+y*y+z*z<1024 && x*x+y*y+z*z>.01, "Invalid review camera offset");
                    ReviewCamera.start(new net.minecraft.world.phys.Vec3(x,y,z),height,!action.has("follow") || action.get("follow").getAsBoolean());
                }
            }
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
            case "iris_pack" -> {
                // Development only: select a pack in Iris directly, like the Iris pack screen does.
                String pack = action.get("name").getAsString();
                require(pack.matches("[A-Za-z0-9 ._()-]{1,80}"), "Invalid pack name");
                var config = net.irisshaders.iris.Iris.getIrisConfig();
                config.setShaderPackName(pack);
                config.setShadersEnabled(true);
                config.save();
                net.irisshaders.iris.Iris.reload();
                com.lowdragmc.photon.client.compat.iris.IrisCompat.invalidate();
            }
            case "spirit_glow" -> {
                // Same path as the settings screen: change the stored toggle; SpiritGlow applies it on its next check.
                cn.wwl.orimod.config.OriClientConfig.SPIRIT_GLOW.set(action.get("enabled").getAsBoolean());
                cn.wwl.orimod.config.OriClientConfig.SPEC.save();
                return Map.of("spiritGlow", cn.wwl.orimod.client.render.SpiritGlow.diagnostics());
            }
            case "visual_quality" -> {
                // Same stored value the settings screen cycles; SpiritGlow maps it to a shader profile on its next check.
                cn.wwl.orimod.config.OriClientConfig.QUALITY.set(cn.wwl.orimod.config.VisualQuality.valueOf(action.get("quality").getAsString()));
                cn.wwl.orimod.config.OriClientConfig.SPEC.save();
                return Map.of("visuals", cn.wwl.orimod.config.OriClientConfig.visuals().toString());
            }
            case "perf_sample" -> {
                // PERF02: live effect, particle and heap counters; gc=true collects first so samples compare retained memory.
                if (action.has("gc") && action.get("gc").getAsBoolean()) { System.gc(); System.gc(); }
                var runtime = Runtime.getRuntime();
                var values = new LinkedHashMap<String, Object>();
                values.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0);
                values.put("heapMaxMb", runtime.maxMemory() / 1048576.0);
                values.put("particles", mc.particleEngine.countParticles());
                values.put("entities", mc.level == null ? 0 : mc.level.getEntityCount());
                values.put("fxLifecycle", cn.wwl.orimod.client.fx.SpiritFxClient.lifecycle());
                values.put("tick", ticks);
                // PERF05: integrated-server tick durations over the last 100 ticks (vanilla's own ring buffer).
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    long[] nanos = server.getTickTimesNanos();
                    double max = 0, sum = 0;
                    for (long n : nanos) { max = Math.max(max, n / 1e6); sum += n / 1e6; }
                    values.put("serverMsptAvg", sum / nanos.length);
                    values.put("serverMsptMax", max);
                }
                return values;
            }
            case "render_diagnostics" -> {
                return Map.of("photon", com.lowdragmc.photon.client.compat.iris.IrisDiagnostics.report(
                        com.lowdragmc.photon.client.compat.iris.IrisCompat.diagnosticsTarget()));
            }
            case "layer_preview" -> { return LayerPreview.configure(action); }
            case "preview_effect" -> {
                return SpiritMotePreview.start(directory, new net.minecraft.core.BlockPos(
                        action.get("x").getAsInt(), action.get("y").getAsInt(), action.get("z").getAsInt()));
            }
            case "stop_preview_effect" -> SpiritMotePreview.stop();
            case "spirit_fx_burst" -> {
                // FX02: play a spirit effect burst locally (same budget and definitions as server-sent bursts).
                var kind = cn.wwl.orimod.fx.SpiritFxKind.valueOf(action.get("kind").getAsString());
                cn.wwl.orimod.client.fx.SpiritFxClient.preview(kind, action.get("x").getAsDouble(), action.get("y").getAsDouble(), action.get("z").getAsDouble(),
                        vec(action, "look"), vec(action, "motion"));
                return Map.of("kind", kind.name(), "definition", cn.wwl.orimod.client.fx.SpiritFxResources.get(kind).toString());
            }
            case "spirit_fx_state" -> { return cn.wwl.orimod.client.fx.SpiritFxResources.diagnostics(); }
            case "settings" -> {
                if (action.has("autoJump")) mc.options.autoJump().set(action.get("autoJump").getAsBoolean());
                if (action.has("mainArm")) mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.valueOf(action.get("mainArm").getAsString()));
                if (action.has("fov")) mc.options.fov().set(Math.clamp(action.get("fov").getAsInt(), 30, 110));
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
            case "open_mod_config" -> {
                // Same path as Mods list -> Config: the factory registered by the shipped client entry point.
                var container = net.neoforged.fml.ModList.get().getModContainerById("orimod").orElseThrow();
                var factory = net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(container.getModInfo());
                require(factory.isPresent(), "orimod registered no config screen");
                mc.setScreen(factory.get().createScreen(container, mc.screen));
            }
            case "mod_config" -> {
                var values = new LinkedHashMap<String, Object>();
                values.put("loaded", cn.wwl.orimod.config.OriClientConfig.SPEC.isLoaded());
                values.put("configVersion", cn.wwl.orimod.config.OriClientConfig.CONFIG_VERSION.get());
                values.put("spiritCamera", cn.wwl.orimod.config.OriClientConfig.SPIRIT_CAMERA.get().name());
                values.put("visuals", cn.wwl.orimod.config.OriClientConfig.visuals().toString());
                values.put("feedback", cn.wwl.orimod.client.fx.SpiritScreenFeedback.diagnostics());
                values.put("fx", cn.wwl.orimod.client.fx.SpiritFxClient.diagnostics());
                values.put("fxLifecycle", cn.wwl.orimod.client.fx.SpiritFxClient.lifecycle());
                values.put("spiritGlow", cn.wwl.orimod.client.render.SpiritGlow.diagnostics());
                values.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
                return values;
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

    private void captureLifecycle(net.minecraft.server.MinecraftServer server) {
        lifecycleSnapshot = Map.of("frozen", cn.wwl.orimod.movement.BashSession.frozenCount(),
                "challenges", cn.wwl.orimod.world.SpiritChallenge.sessionCount(),
                "regionTickets", searchRegionTickets(server),
                "locator", cn.wwl.orimod.world.RuinLocator.lifecycleState());
    }
    private void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) { captureLifecycle(event.getServer()); }
    private void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { captureLifecycle(event.getServer()); }

    private static int searchRegionTickets(net.minecraft.server.MinecraftServer server) {
        // Development-only observation of vanilla tickets, independent of search bookkeeping.
        try {
            var managerField = net.minecraft.server.level.ServerChunkCache.class.getDeclaredField("distanceManager"); managerField.setAccessible(true);
            var ticketsField = net.minecraft.server.level.DistanceManager.class.getDeclaredField("tickets"); ticketsField.setAccessible(true);
            int count = 0;
            for (var level : server.getAllLevels()) {
                var tickets = (java.util.Map<?, ?>)ticketsField.get(managerField.get(level.getChunkSource()));
                for (var set : tickets.values()) for (var ticket : (Iterable<?>)set)
                    if (((net.minecraft.server.level.Ticket<?>)ticket).getType().toString().equals("orimod_ruin_search")) count++;
            }
            return count;
        } catch (ReflectiveOperationException e) { return -1; }
    }

    private void frame(RenderFrameEvent.Post event) {
        frames++;
        long now = System.nanoTime();
        video.frame(now);
        var mc = Minecraft.getInstance();
        if (recordingPose && !mc.isPaused() && mc.player != null && poses.size() < 120_000) {
            poses.add(Map.of("frame", frames, "nanos", now, "tick", mc.player.tickCount,
                    "onGround", mc.player.onGround(), "animation", cn.wwl.orimod.client.render.SpiritPlayerVisual.diagnostics(mc.player)));
        }
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
        state.put("serverLifecycle", lifecycleSnapshot);
        state.put("inputScriptError", inputScriptError);
        state.put("frame", frames);
        state.put("screen", mc.screen == null ? "gameplay" : mc.screen.getClass().getSimpleName());
        state.put("windowActive", mc.isWindowActive());
        state.put("fps", mc.getFps());
        state.put("renderDistance", mc.options.renderDistance().get());
        state.put("vsync", mc.options.enableVsync().get());
        state.put("fpsLimit", mc.options.framerateLimit().get());
        state.put("autoJump", mc.options.autoJump().get());
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
        state.put("reviewCamera", ReviewCamera.active());
        state.put("mods", ModList.get().getMods().stream().map(mod -> mod.getModId() + "@" + mod.getVersion()).toList());
        if (mc.player != null && mc.level != null) {
            var player = mc.player;
            state.put("player", Map.of("position", new double[]{player.getX(), player.getY(), player.getZ()},
                    "velocity", new double[]{player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z},
                    "yaw", player.getYRot(), "pitch", player.getXRot(), "onGround", player.onGround(),
                    "health", player.getHealth(), "pose", player.getPose().name(),
                    "dimension", mc.level.dimension().location().toString()));
            if (mc.hitResult != null) {
                var hit = new LinkedHashMap<String, Object>();
                hit.put("type", mc.hitResult.getType().name());
                var point = mc.hitResult.getLocation();
                hit.put("position", new double[]{point.x, point.y, point.z});
                if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult block) {
                    var pos = block.getBlockPos();
                    hit.put("block", new int[]{pos.getX(), pos.getY(), pos.getZ()});
                    hit.put("face", block.getDirection().name());
                }
                if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entity) hit.put("entity", entity.getEntity().getId());
                state.put("hit", hit);
            }
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
        if (player.level().isClientSide) state.put("animation", cn.wwl.orimod.client.render.SpiritPlayerVisual.diagnostics(player));
        state.put("vanilla", Map.of("pose", player.getPose().name(), "fallFlying", player.isFallFlying(),
                "flightTicks", player.getFallFlyingTicks(), "spinAttack", player.isAutoSpinAttack(),
                "inWater", player.isInWaterOrBubble(), "wet", player.isInWaterOrRain(), "fireTicks", player.getRemainingFireTicks(),
                "armor", player.getArmorValue(), "armorToughness", player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)));
        state.put("survival", Map.of("food", player.getFoodData().getFoodLevel(), "saturation", player.getFoodData().getSaturationLevel(),
                "exhaustion", player.getFoodData().getExhaustionLevel(), "air", player.getAirSupply(), "maxAir", player.getMaxAirSupply(),
                "effects", player.getActiveEffects().stream().map(effect -> Map.of("id", effect.getEffect().getRegisteredName(),
                        "amplifier", effect.getAmplifier(), "duration", effect.getDuration())).toList()));
        state.put("transport", Map.of("passenger", player.isPassenger(), "vehicle", player.getVehicle() == null ? "none" : player.getVehicle().getType().toString(),
                "sleeping", player.isSleeping(), "climbable", player.onClimbable(), "swimming", player.isVisuallySwimming()));
        state.put("collision", Map.of("stepHeight", player.maxUpStep(), "horizontal", player.horizontalCollision,
                "vertical", player.verticalCollision, "crouching", player.isCrouching(), "flying", player.getAbilities().flying,
                "forward", player.zza, "strafe", player.xxa, "bounds", player.getBoundingBox().toString()));
        var movement = player.getData(cn.wwl.orimod.spirit.SpiritData.MOVEMENT);
        var combat = player.getData(cn.wwl.orimod.spirit.SpiritData.COMBAT);
        var view = player.getData(cn.wwl.orimod.spirit.SpiritData.COMBAT_VIEW);
        state.put("combat", Map.ofEntries(Map.entry("view", view), Map.entry("mode", combat.mode), Map.entry("stage", combat.stage),
                Map.entry("startedAt", combat.startedAt), Map.entry("buffered", combat.buffered), Map.entry("bladeStarts", combat.bladeStarts),
                Map.entry("bladeHits", combat.bladeHits), Map.entry("bowShots", combat.bowShots), Map.entry("cancellations", combat.cancellations),
                Map.entry("lastCancel", combat.lastCancel), Map.entry("usingItem", player.isUsingItem()),
                Map.entry("useTicks", player.getTicksUsingItem()), Map.entry("energy", cn.wwl.orimod.progression.SpiritProgression.energy(player))));
        state.put("movement", Map.ofEntries(Map.entry("mode", movement.mode.id()), Map.entry("activeTicks", movement.activeTicks),
                Map.entry("airJumpSpent", movement.airJumpSpent), Map.entry("airDashSpent", movement.airDashSpent),
                Map.entry("inputSequence", movement.sequence), Map.entry("inputActive", movement.active),
                Map.entry("lastAction", movement.lastAction.id()), Map.entry("wallDirection", movement.wallDirection),
                Map.entry("jumps", movement.acceptedJumps), Map.entry("wallJumps", movement.acceptedWallJumps),
                Map.entry("dashes", movement.acceptedDashes), Map.entry("rejectedDashes", movement.rejectedDashes),
                Map.entry("dashUnlocked", cn.wwl.orimod.spirit.SpiritData.hasDash(player)),
                Map.entry("gameTime", player.level().getGameTime()), Map.entry("jumpUntil", movement.jumpUntil),
                Map.entry("jumpPressedAt", movement.jumpPressedAt),
                Map.entry("pendingJumps", movement.jumps.size()), Map.entry("pendingPredictions", movement.predictedJumps.size()),
                Map.entry("predictedJumps", movement.predictedJumpCount), Map.entry("predictionMismatches", movement.jumpPredictionMismatches),
                Map.entry("predictionRejections", movement.jumpPredictionRejections),
                Map.entry("groundedAt", movement.groundedAt), Map.entry("groundJumpAvailable", movement.groundJumpAvailable),
                Map.entry("lastWallRefill", movement.lastWallRefill),
                Map.entry("relocations", movement.relocations),
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

    private static net.minecraft.world.phys.Vec3 vec(com.google.gson.JsonObject action, String key) {
        if (!action.has(key)) return null;
        var a = action.getAsJsonArray(key);
        var v = new net.minecraft.world.phys.Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        require(Double.isFinite(v.lengthSqr()) && v.lengthSqr() > 1e-6, "Invalid " + key);
        return v.normalize();
    }
}
