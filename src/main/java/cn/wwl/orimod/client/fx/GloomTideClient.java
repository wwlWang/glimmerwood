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
    private static int lastParticles;
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
        if (++ticks % 20 != 0 || mc.player == null) return;
        lastParticles=0;
        float local=BlightAtmosphereClient.intensity(1);
        if(Math.max(FADE.value(1),local)<=0||BlightAtmosphereClient.vanillaPriority())return;
        var visuals = OriClientConfig.visuals();
        int count = FxBudget.tideParticles(visuals.quality(), visuals.advancedParticles());
        double radius = local>0?6:Math.max(16, Math.min(96, mc.options.getEffectiveRenderDistance() * 16 * .75));
        for (int i = 0; i < count; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double x=mc.player.getX()+Math.cos(angle)*radius,z=mc.player.getZ()+Math.sin(angle)*radius;
            if(local>0&&BlightAtmosphereClient.target(x,z)<=0)continue;
            // Force distance visibility, while FxBudget still caps the total at four per second.
            world.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), GloomLook.MIST, .45f, 60), true,
                    mc.player.getX() + Math.cos(angle) * radius, mc.player.getY() + 2 + world.random.nextDouble() * 5,
                    mc.player.getZ() + Math.sin(angle) * radius, .01, .012, -.01);
            lastParticles++;
        }
    }
    public static float intensity(float partial) {
        if (world == null || world != Minecraft.getInstance().level || world.dimension() != Level.OVERWORLD) return 0;
        return FADE.value(partial);
    }
    public static void fog(ViewportEvent.ComputeFogColor event) {
        float f = BlightAtmosphereRules.combined(intensity((float)event.getPartialTick()),BlightAtmosphereClient.intensity((float)event.getPartialTick()),BlightAtmosphereClient.vanillaPriority());
        event.setRed(GloomTideVisuals.mix(event.getRed(), 0x2A, f));
        event.setGreen(GloomTideVisuals.mix(event.getGreen(), 0x16, f));
        event.setBlue(GloomTideVisuals.mix(event.getBlue(), 0x38, f));
    }
    public static Vec3 sky(Vec3 original, float partial) {
        float f=BlightAtmosphereRules.combined(OriClientConfig.visuals().advancedParticles()?intensity(partial):0,BlightAtmosphereClient.intensity(partial),BlightAtmosphereClient.vanillaPriority());
        return original.lerp(Vec3.fromRGB24(GloomTideVisuals.COLOR), f);
    }
    public static float stars(float original, float partial) {
        return OriClientConfig.visuals().advancedParticles() ? original * (1 - intensity(partial) * .6f) : original;
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static java.util.Map<String, Object> diagnostics() {
        return java.util.Map.of("intensity", intensity(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)),
                "target", FADE.target(),"particlesLastSecond",lastParticles,"blight",BlightAtmosphereClient.diagnostics());
    }
    private static void bind(ClientLevel level) {
        world = level; ticks = 0;
        FADE.bind(level != null && level.dimension() == Level.OVERWORLD);
    }
    private static void reset() { world = null; FADE.disconnect(); ticks = 0; }
    private GloomTideClient() {}
}
