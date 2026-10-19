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
    static ParticleOptions particle(String id) {
        var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(net.minecraft.resources.ResourceLocation.tryParse(id));
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
        burst(payload.kind(), payload.x(), payload.y(), payload.z(), payload.sourceEntity());
    }

    /** Client tick: keep persistent effects alive only while the world, the entity and the heartbeat are. */
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level != trackedLevel) { TRACKER.clear().forEach(SpiritFxClient::end); trackedLevel = level; }
        if (level == null || mc.isPaused()) return;
        TRACKER.tick(level.getGameTime(), id -> { var e = level.getEntity(id); return e != null && e.isAlive() && !e.isRemoved(); })
                .forEach(SpiritFxClient::end);
        for (var key : TRACKER.active()) {
            var entity = level.getEntity(key.entity());
            if (entity != null) burst(key.kind(), entity.getX(), entity.getY() + entity.getBbHeight() * .5, entity.getZ(), key.entity());
        }
    }

    public static void loggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        TRACKER.clear().forEach(SpiritFxClient::end); trackedLevel = null;
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
    public static void preview(SpiritFxKind kind, double x, double y, double z) {
        var mc = Minecraft.getInstance();
        burst(kind, x, y, z, mc.player == null ? -1 : mc.player.getId());
    }

    private static void burst(SpiritFxKind kind, double px, double py, double pz, int source) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        var visuals = OriClientConfig.visuals();
        var split = FxBudget.of(kind, visuals.quality(), visuals.advancedParticles());
        var definition = SpiritFxResources.get(kind);
        if (!kind.persistent()) received++;
        LAST.put(kind.name(), java.util.Map.of("essential", split.essential(), "decorative", split.decorative(), "quality", visuals.quality().name(), "advanced", visuals.advancedParticles()));
        for (int i = 0; i < split.total(); i++) {
            // Same distribution as ServerLevel.sendParticles: gaussian offset and speed, per definition layer.
            var layer = definition.layerFor(i, split.total());
            double x = px + RANDOM.nextGaussian() * layer.spreadX();
            double y = py + RANDOM.nextGaussian() * layer.spreadY();
            double z = pz + RANDOM.nextGaussian() * layer.spreadZ();
            double vx = RANDOM.nextGaussian() * layer.speed(), vy = RANDOM.nextGaussian() * layer.speed(), vz = RANDOM.nextGaussian() * layer.speed();
            boolean essential = i < split.essential();
            try { level.addParticle(particle(layer.particle()), essential, x, y, z, vx, vy, vz); }
            catch (Throwable ignored) { return; }
        }
        if (kind == SpiritFxKind.STOMP_IMPACT && mc.player != null && source == mc.player.getId()) {
            SpiritScreenFeedback.stomp(visuals);
        }
    }
}
