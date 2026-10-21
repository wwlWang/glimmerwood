package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.network.GloomTidePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Fog is always available; advanced particles also enables the small sky/star tint. */
public final class GloomTideClient {
    private static ClientLevel world;
    private static final GloomTideVisuals.Fade FADE = new GloomTideVisuals.Fade();
    private static int ticks;
    public static void receive(GloomTidePayload payload) {
        var level = Minecraft.getInstance().level;
        if (world != level) bind(level);
        FADE.receive(payload.active(), payload.intensity());
    }
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (world != mc.level) bind(mc.level);
        if (world == null || mc.isPaused()) return;
        FADE.tick();
        if (++ticks % 20 != 0 || FADE.value(1) <= 0 || mc.player == null) return;
        var visuals = OriClientConfig.visuals();
        int count = FxBudget.tideParticles(visuals.quality(), visuals.advancedParticles());
        double radius = Math.max(16, Math.min(96, mc.options.getEffectiveRenderDistance() * 16 * .75));
        for (int i = 0; i < count; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            // Force distance visibility, while FxBudget still caps the total at four per second.
            world.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), GloomLook.MIST, .45f, 60), true,
                    mc.player.getX() + Math.cos(angle) * radius, mc.player.getY() + 2 + world.random.nextDouble() * 5,
                    mc.player.getZ() + Math.sin(angle) * radius, .01, .012, -.01);
        }
    }
    public static float intensity(float partial) {
        if (world == null || world != Minecraft.getInstance().level || world.dimension() != Level.OVERWORLD) return 0;
        return FADE.value(partial);
    }
    public static void fog(ViewportEvent.ComputeFogColor event) {
        float f = intensity((float) event.getPartialTick());
        event.setRed(GloomTideVisuals.mix(event.getRed(), 0x2A, f));
        event.setGreen(GloomTideVisuals.mix(event.getGreen(), 0x16, f));
        event.setBlue(GloomTideVisuals.mix(event.getBlue(), 0x38, f));
    }
    public static Vec3 sky(Vec3 original, float partial) {
        if (!OriClientConfig.visuals().advancedParticles()) return original;
        return original.lerp(Vec3.fromRGB24(GloomTideVisuals.COLOR), intensity(partial));
    }
    public static float stars(float original, float partial) {
        return OriClientConfig.visuals().advancedParticles() ? original * (1 - intensity(partial) * .6f) : original;
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static java.util.Map<String, Object> diagnostics() {
        return java.util.Map.of("intensity", intensity(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)),
                "target", FADE.target());
    }
    private static void bind(ClientLevel level) {
        world = level; ticks = 0;
        FADE.bind(level != null && level.dimension() == Level.OVERWORLD);
    }
    private static void reset() { world = null; FADE.disconnect(); ticks = 0; }
    private GloomTideClient() {}
}
