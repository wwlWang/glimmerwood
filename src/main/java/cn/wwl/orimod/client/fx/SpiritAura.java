package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.client.render.SpiritEnergy;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * R88: the faint luminescence of a spirit (playtest: "那股微弱发光的感觉也没做出来"), without needing the Iris pack.
 * Each visible spirit body carries two soft camera-facing glows that breathe slowly, plus the companion light drawn as a
 * smooth interpolated orb (core + wide halo) instead of stacking one opaque particle per tick. Everything is additive
 * and low alpha, so it reads as soft light, never as a solid blob.
 */
public final class SpiritAura {
    public static final int BODY = 0xBFE4FF, BODY_CORE = 0xEAF6FF, COMPANION = 0xF2F8FF, COMPANION_HALO = 0x9EC8FF;
    /** Peak alphas: wide body halo, inner body glow, companion core / halo / outer. */
    public static final float BODY_HALO_ALPHA = .18f, BODY_INNER_ALPHA = .26f, COMPANION_CORE_ALPHA = .95f,
            COMPANION_HALO_ALPHA = .3f, COMPANION_OUTER_ALPHA = .09f;
    private SpiritAura() {}

    /** 0.8..1 slow breathing (same period as the body's breath). */
    public static float breath(float seconds) {
        return .9f + .1f * Mth.sin(seconds * Mth.TWO_PI / cn.wwl.orimod.movement.FeelTuning.BREATH_PERIOD);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null) return;
        var camera = event.getCamera();
        var cam = camera.getPosition();
        var rotation = camera.rotation();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // LevelRenderer already applies the camera rotation at this stage; positions are camera-relative.
        var poses = new com.mojang.blaze3d.vertex.PoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(SpiritEnergy.glow());
        var pose = poses.last();
        float t = SpiritEnergy.time();
        boolean drew = false;
        for (Player player : level.players()) {
            if (!SpiritData.isSpirit(player) || player.isSpectator() || player.isInvisible() || player.distanceToSqr(mc.player) > 48 * 48) continue;
            boolean self = player == mc.player && mc.options.getCameraType().isFirstPerson();
            var at = player.getPosition(partial);
            float b = breath(t + player.getId() * .37f), h = player.getBbHeight();
            if (!self) {
                var chest = new Vector3f((float) (at.x - cam.x), (float) (at.y + h * .5 - cam.y), (float) (at.z - cam.z));
                SpiritEnergy.billboard(out, pose, chest, rotation, h * .95f, BODY, BODY_HALO_ALPHA * b);
                SpiritEnergy.billboard(out, pose, chest, rotation, h * .5f, BODY_CORE, BODY_INNER_ALPHA * b);
            }
            // Companion (never in your own first-person face).
            if (!self) {
                long gt = level.getGameTime();
                var c0 = SpiritFxClient.companion(player, gt); var c1 = SpiritFxClient.companion(player, gt + 1);
                // companion() reads the owner's current position; re-anchor on the interpolated body.
                double ox = at.x - player.getX(), oy = at.y - player.getY(), oz = at.z - player.getZ();
                var c = new Vector3f((float) (Mth.lerp(partial, c0.x, c1.x) + ox - cam.x), (float) (Mth.lerp(partial, c0.y, c1.y) + oy - cam.y),
                        (float) (Mth.lerp(partial, c0.z, c1.z) + oz - cam.z));
                SpiritEnergy.billboard(out, pose, c, rotation, .7f, COMPANION_HALO, COMPANION_OUTER_ALPHA * b);
                SpiritEnergy.billboard(out, pose, c, rotation, .3f, COMPANION_HALO, COMPANION_HALO_ALPHA * b);
                SpiritEnergy.billboard(out, pose, c, rotation, .1f, COMPANION, COMPANION_CORE_ALPHA);
            }
            drew = true;
        }
        if (drew) buffers.endBatch(SpiritEnergy.glow());
    }
}
