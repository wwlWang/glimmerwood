package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.fx.FxBudget;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.network.SpiritFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Spawns spirit effect bursts under the player's quality and particle settings. */
public final class SpiritFxClient {
    private static final RandomSource RANDOM = RandomSource.create();
    private SpiritFxClient() {}

    /** Particle for a definition layer; an unknown or non-simple particle id falls back to the end rod glow. */
    static ParticleOptions particle(cn.wwl.orimod.fx.SpiritFxDefinition.Layer layer, float taper) {
        var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(net.minecraft.resources.ResourceLocation.tryParse(layer.particle()));
        if (type instanceof cn.wwl.orimod.fx.SpiritParticleOptions.Type spirit)
            return new cn.wwl.orimod.fx.SpiritParticleOptions(spirit, layer.rgb(), layer.size() * taper, layer.lifetime());
        return type instanceof net.minecraft.core.particles.SimpleParticleType simple ? simple : ParticleTypes.END_ROD;
    }

    private static final java.util.Map<String, Object> LAST = new java.util.concurrent.ConcurrentHashMap<>();
    private static int received;

    /** Read-only state for the development bridge: last spawn budget per effect kind. */
    public static java.util.Map<String, Object> diagnostics() {
        var copy = new java.util.TreeMap<String, Object>(LAST);
        copy.put("received", received);
        return copy;
    }

    private static final cn.wwl.orimod.fx.SpiritFxTracker TRACKER = new cn.wwl.orimod.fx.SpiritFxTracker();
    private static final java.util.Map<cn.wwl.orimod.fx.SpiritFxTracker.Key, SpiritLoopSound> LOOPS = new java.util.HashMap<>();
    private static Object trackedLevel;
    private static final it.unimi.dsi.fastutil.ints.Int2ObjectMap<cn.wwl.orimod.fx.FlashLight> FLASHES = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private static int started, stopped;

    /** Read-only lifecycle state for the development bridge and smoke scripts. */
    public static java.util.Map<String, Object> lifecycle() {
        var active = new java.util.ArrayList<String>();
        for (var key : TRACKER.active()) active.add(key.entity() + ":" + key.kind().name());
        return java.util.Map.of("active", active, "loops", LOOPS.size(), "started", started, "stopped", stopped);
    }

    public static void play(SpiritFxPayload payload) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        switch (payload.phase()) {
            case START -> { if (TRACKER.start(payload.sourceEntity(), payload.kind(), level.getGameTime())) { started++; startLoop(payload.sourceEntity(), payload.kind()); } return; }
            case STOP -> { if (TRACKER.stop(payload.sourceEntity(), payload.kind())) end(new cn.wwl.orimod.fx.SpiritFxTracker.Key(payload.sourceEntity(), payload.kind())); return; }
            case STOP_ALL -> { TRACKER.stopAll(payload.sourceEntity()).forEach(SpiritFxClient::end); return; }
            case BURST -> {}
        }
        if (payload.kind() == SpiritFxKind.SPIRIT_FLAME && level.getEntity(payload.sourceEntity()) instanceof net.minecraft.world.entity.player.Player player)
            CompanionMotion.attack(player, new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()));
        burst(payload.kind(), payload.x(), payload.y(), payload.z(), payload.sourceEntity());
    }

    /** Client tick: keep persistent effects alive only while the world, the entity and the heartbeat are. */
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level != trackedLevel) { TRACKER.clear().forEach(SpiritFxClient::end); FLASHES.clear(); trackedLevel = level; }
        if (level == null || mc.isPaused()) { CompanionMotion.tick(); return; }
        TRACKER.tick(level.getGameTime(), id -> { var e = level.getEntity(id); return e != null && e.isAlive() && !e.isRemoved(); })
                .forEach(SpiritFxClient::end);
        for (var key : TRACKER.active()) if (key.kind() == SpiritFxKind.FLASH) FLASHES.computeIfAbsent(key.entity(), id -> new cn.wwl.orimod.fx.FlashLight());
        FLASHES.int2ObjectEntrySet().removeIf(entry -> {
            var entity = level.getEntity(entry.getIntKey());
            if (entity == null || !entity.isAlive()) return true;
            entry.getValue().tick(TRACKER.isActive(entry.getIntKey(), SpiritFxKind.FLASH) && (entity != mc.player || mc.screen == null));
            return entry.getValue().ended();
        });
        CompanionMotion.tick();
        companions(mc, level);
        for (var key : TRACKER.active()) {
            var entity = level.getEntity(key.entity());
            if (entity != null) burst(key.kind(), entity.getX(), entity.getY() + entity.getBbHeight() * .5, entity.getZ(), key.entity());
        }
    }

    /** R37: where a spirit player's companion light floats (orbiting near the shoulder); shared by the orb and flame beams. */
    public static net.minecraft.world.phys.Vec3 companion(net.minecraft.world.entity.Entity owner, long gameTime) {
        double a = gameTime * .08 + owner.getId();
        return new net.minecraft.world.phys.Vec3(owner.getX() + Math.cos(a) * .55, owner.getY() + owner.getBbHeight() * .9 + Math.sin(gameTime * .15) * .08,
                owner.getZ() + Math.sin(a) * .55);
    }

    /** R37: one glowing mote per tick at each nearby spirit player's companion: a small Sein-like light with a short trail. */
    private static void companions(Minecraft mc, net.minecraft.client.multiplayer.ClientLevel level) {
        if (mc.player == null) return;
        for (var player : level.players()) {
            if (!cn.wwl.orimod.spirit.SpiritData.isSpirit(player) || player.isSpectator() || player.distanceToSqr(mc.player) > 32 * 32) continue;
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;   // never in your own face
            var state = CompanionMotion.state(player);
            if (state != null && state.vel.length() > CompanionMotion.RUN_SPEED) continue;
            var at = CompanionMotion.position(player, 1);
            var prev = CompanionMotion.position(player, 0);
            try {
                // R88: the orb itself is drawn smoothly by SpiritAura; particles only leave a sparse soft trail.
                if ((level.getGameTime() + player.getId()) % 2 == 0) level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), 0x9EC8FF, .14f, 10), false,
                        (at.x + prev.x) * .5, (at.y + prev.y) * .5, (at.z + prev.z) * .5, 0, .01, 0);
            } catch (Throwable ignored) { return; }
        }
    }

    public static void loggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        TRACKER.clear().forEach(SpiritFxClient::end); FLASHES.clear(); trackedLevel = null; CompanionMotion.clear();
    }

    public static boolean flashing(int entity) { return TRACKER.isActive(entity, SpiritFxKind.FLASH); }
    public static float flashAmount(net.minecraft.world.entity.player.Player player, float partial) {
        if (player == Minecraft.getInstance().player && Minecraft.getInstance().screen != null) return 0;
        var light = FLASHES.get(player.getId());
        return light == null ? 0 : light.amount(partial);
    }

    private static void startLoop(int entityId, SpiritFxKind kind) {
        var cue = cn.wwl.orimod.fx.SpiritSoundCue.of(kind);
        var mc = Minecraft.getInstance();
        if (cue == null || !cue.loop() || mc.level == null) return;
        var entity = mc.level.getEntity(entityId);
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(net.minecraft.resources.ResourceLocation.parse(cue.sound()));
        if (entity == null || sound == null) return;
        var loop = new SpiritLoopSound(sound, entity, cue.volume(), cue.pitch());
        LOOPS.put(new cn.wwl.orimod.fx.SpiritFxTracker.Key(entityId, kind), loop);
        mc.getSoundManager().play(loop);
    }

    private static void end(cn.wwl.orimod.fx.SpiritFxTracker.Key key) {
        stopped++;
        var loop = LOOPS.remove(key);
        if (loop != null) { loop.end(); Minecraft.getInstance().getSoundManager().stop(loop); }
    }

    /** Development preview: one burst of {@code kind} at a position, through the same settings and definitions. */
    public static void preview(SpiritFxKind kind, double x, double y, double z) { preview(kind, x, y, z, null, null); }

    /** Preview with explicit facing/motion (for look sheets shot from a fixed camera); null uses the player's. */
    public static void preview(SpiritFxKind kind, double x, double y, double z, net.minecraft.world.phys.Vec3 look, net.minecraft.world.phys.Vec3 motion) {
        var mc = Minecraft.getInstance();
        burst(kind, x, y, z, mc.player == null ? -1 : mc.player.getId(), look, motion);
    }

    private static void burst(SpiritFxKind kind, double px, double py, double pz, int source) { burst(kind, px, py, pz, source, null, null); }

    private static void burst(SpiritFxKind kind, double px, double py, double pz, int source, net.minecraft.world.phys.Vec3 lookOverride, net.minecraft.world.phys.Vec3 motionOverride) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        var visuals = OriClientConfig.visuals();
        var split = FxBudget.of(kind, visuals.quality(), visuals.advancedParticles());
        var definition = SpiritFxResources.get(kind);
        if (!kind.persistent()) received++;
        LAST.put(kind.name(), java.util.Map.of("essential", split.essential(), "decorative", split.decorative(), "quality", visuals.quality().name(), "advanced", visuals.advancedParticles()));
        var sourceEntity = level.getEntity(source);
        // Facing for arcs and motion for trails: the source's own look and velocity (fallbacks for markers).
        net.minecraft.world.phys.Vec3 look = sourceEntity == null ? new net.minecraft.world.phys.Vec3(0, 0, 1) : sourceEntity.getLookAngle();
        net.minecraft.world.phys.Vec3 motion = sourceEntity == null ? net.minecraft.world.phys.Vec3.ZERO : sourceEntity.getDeltaMovement();
        if (lookOverride != null) look = lookOverride;
        if (motionOverride != null) motion = motionOverride;
        if (motion.lengthSqr() < 1e-4) motion = look;
        var dir = motion.normalize();
        double phase = kind.persistent() ? level.getGameTime() * .35 : RANDOM.nextDouble() * Math.PI * 2;
        double yaw = Math.atan2(look.z, look.x);
        int total = split.total();
        for (int i = 0; i < total; i++) {
            var slot = definition.slot(i, total);
            var layer = slot.layer();
            double k = slot.fraction(), x = px, y = py, z = pz, vx = 0, vy = 0, vz = 0;
            float taper = 1;
            switch (layer.shape()) {
                case "ring" -> {
                    double a = phase + Math.PI * 2 * slot.j() / slot.n(), cx = Math.cos(a), cz = Math.sin(a);
                    x += cx * layer.radius(); z += cz * layer.radius();
                    vx = cx * layer.outward(); vz = cz * layer.outward();
                }
                case "arc" -> {
                    double a = yaw + Math.toRadians(-70 + 140 * k), cx = Math.cos(a), cz = Math.sin(a);
                    x += cx * layer.radius(); z += cz * layer.radius(); y += (k - .5) * layer.length();
                    vx = -cz * layer.outward(); vz = cx * layer.outward();     // tangential sweep
                    taper = (float) (.6 + .4 * Math.sin(Math.PI * k));
                }
                case "line" -> {
                    x -= dir.x * layer.length() * k; y -= dir.y * layer.length() * k; z -= dir.z * layer.length() * k;
                    vx = -dir.x * layer.outward(); vy = -dir.y * layer.outward(); vz = -dir.z * layer.outward();
                    taper = (float) (1 - .65 * k);
                }
                case "beam" -> {
                    // From the companion beside the source's shoulder to the burst point (the struck enemy).
                    if (sourceEntity != null) {
                        var from = sourceEntity instanceof net.minecraft.world.entity.player.Player player
                                ? CompanionMotion.position(player, mc.getTimer().getGameTimeDeltaPartialTick(false))
                                : companion(sourceEntity, level.getGameTime());
                        x = from.x + (px - from.x) * k; y = from.y + (py - from.y) * k; z = from.z + (pz - from.z) * k;
                    }
                    taper = (float) (.55 + .45 * k);
                }
                case "column" -> {
                    double a = phase + Math.PI * 2 * slot.j() * .618;
                    x += Math.cos(a) * layer.radius(); z += Math.sin(a) * layer.radius(); y += layer.length() * k;
                    vy = layer.outward();
                }
                case "gather" -> {
                    // R100: originate around the root heart and converge on the recipient's chest.
                    double a = phase + Math.PI * 2 * k;
                    x += Math.cos(a) * layer.radius(); z += Math.sin(a) * layer.radius();
                    y += k * layer.length();
                    if (sourceEntity != null) {
                        double friction = layer.particle().equals("orimod:spirit_spark") ? SpiritParticle.SPARK_FRICTION : SpiritParticle.MOTE_FRICTION;
                        double travel = (1 - Math.pow(friction, layer.lifetime())) / (1 - friction);
                        vx = (sourceEntity.getX() - x) / travel;
                        vy = (sourceEntity.getY() + sourceEntity.getBbHeight() * .6 - y) / travel;
                        vz = (sourceEntity.getZ() - z) / travel;
                    }
                }
                default -> {}
            }
            // Same distribution as ServerLevel.sendParticles: gaussian offset and speed, per definition layer.
            x += RANDOM.nextGaussian() * layer.spreadX(); y += RANDOM.nextGaussian() * layer.spreadY(); z += RANDOM.nextGaussian() * layer.spreadZ();
            vx += RANDOM.nextGaussian() * layer.speed(); vy += RANDOM.nextGaussian() * layer.speed(); vz += RANDOM.nextGaussian() * layer.speed();
            boolean essential = i < split.essential();
            try { level.addParticle(particle(layer, taper), essential, x, y, z, vx, vy, vz); }
            catch (Throwable ignored) { return; }
        }
        if (kind == SpiritFxKind.STOMP_IMPACT && mc.player != null && source == mc.player.getId()) {
            SpiritScreenFeedback.stomp(visuals);
        }
    }
}
