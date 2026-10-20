package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.fx.ScreenFeedback;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Applies hurt/stomp camera shake and hurt flash in spirit form, scaled by the client settings. */
public final class SpiritScreenFeedback {
    private static final ScreenFeedback FEEDBACK = new ScreenFeedback();
    private static int lastHurtTime;
    private SpiritScreenFeedback() {}

    /** Read-only state for the development bridge. */
    public static java.util.Map<String, Object> diagnostics() {
        var angles = FEEDBACK.angles(0);
        return java.util.Map.of("active", FEEDBACK.active(), "flashAlpha", FEEDBACK.flashAlpha(0),
                "pitch", angles.pitch(), "yaw", angles.yaw(), "roll", angles.roll(), "hurtTriggers", hurtTriggers, "lastShake", FEEDBACK.lastShakeAmplitude(), "lastFlash", FEEDBACK.lastFlashAlpha(), "stompTriggers", stompTriggers);
    }
    private static int hurtTriggers, stompTriggers;

    static void stomp(OriClientConfig.Visuals visuals) { stompTriggers++; FEEDBACK.stomp(visuals.screenShake()); }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || !SpiritData.isSpirit(player) || !player.isAlive()) { FEEDBACK.clear(); lastHurtTime = 0; return; }
        var visuals = OriClientConfig.visuals();
        // Vanilla sets hurtTime to hurtDuration when the damage event arrives; a rise marks a new hit.
        if (player.hurtTime > lastHurtTime) { hurtTriggers++; FEEDBACK.hurt(visuals.screenShake(), visuals.screenFlash()); }
        lastHurtTime = player.hurtTime;
        FEEDBACK.tick();
        // Settings lowered to zero while an effect plays: stop immediately rather than finishing at the old strength.
        if (visuals.screenShake() == 0 && visuals.screenFlash() == 0) FEEDBACK.clear();
    }

    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!FEEDBACK.active() || Minecraft.getInstance().isPaused()) return;
        var angles = FEEDBACK.angles(event.getPartialTick());
        if (angles.isZero()) return;
        event.setPitch(event.getPitch() + (float)angles.pitch());
        event.setYaw(event.getYaw() + (float)angles.yaw());
        event.setRoll(event.getRoll() + (float)angles.roll());
    }

    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath("orimod", "spirit_flash"), SpiritScreenFeedback::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        var visuals = OriClientConfig.visuals();
        if (mc.player != null) {
            float exposure = cn.wwl.orimod.fx.FlashLight.exposure(SpiritFxClient.flashAmount(mc.player, delta.getGameTimeDeltaPartialTick(false)),
                    visuals.quality(), visuals.advancedParticles(), visuals.screenFlash());
            if (exposure > 0) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(exposure * 255) << 24 | 0xFFFFFF);
        }
        if (!FEEDBACK.active()) return;
        double alpha = FEEDBACK.flashAlpha(delta.getGameTimeDeltaPartialTick(false));
        if (alpha <= 0.002) return;
        int a = (int)Math.round(Math.clamp(alpha, 0, 1) * 255);
        // Cool spirit-white instead of vanilla red, so it reads as the spirit taking the hit.
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), a << 24 | 0xE6F2FF);
    }
}
