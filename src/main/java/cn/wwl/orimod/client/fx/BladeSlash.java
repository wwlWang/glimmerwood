package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.combat.BladeArc;
import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.client.render.SpiritEnergy;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * The visible Spirit Edge: every slash draws a crescent of light through its hit fan (user feedback: "砍的不像 ori
 * 系列那种范围性，带明显的效果"). The crescent sweeps out over the swing, its leading edge brightest, then fades. Stages
 * 1 and 2 cross diagonally in opposite directions; the finisher is a taller overhead arc. Drawn from the synced combat
 * timeline, so other players' slashes show too, in first and third person alike.
 */
public final class BladeSlash {
    private static final int SEGMENTS = 28, CORE = 0xF6FCFF, HALO = 0x84CFFF;
    /** Ticks the crescent takes to sweep out, and to fade after the hit window. */
    private static final float SWEEP = 3, FADE = 7;
    private BladeSlash() {}

    /** 0..1 progress of the sweep and the overall opacity at {@code elapsed} ticks into a stage. */
    public static float sweep(int stage, float elapsed) {
        return Mth.clamp((elapsed - (SpiritCombat.windup(stage) - 1)) / SWEEP, 0, 1);
    }

    public static float opacity(int stage, float elapsed) {
        float start = SpiritCombat.windup(stage) - 1, hold = SpiritCombat.recoveryStart(stage);
        if (elapsed < start) return 0;
        return elapsed <= hold ? 1 : Mth.clamp(1 - (elapsed - hold) / FADE, 0, 1);
    }

    /** Near-eye ribbons disappear by recovery; their last .75 hit-tick fades instead of leaving a line on the reticle.
     * The world-space crescent seen by other players keeps its existing seven-tick tail. */
    public static float opacity(int stage, float elapsed, boolean firstPerson) {
        if (!firstPerson) return opacity(stage, elapsed);
        float end = SpiritCombat.recoveryStart(stage);
        return opacity(stage, elapsed) * Mth.clamp((end - elapsed) / .75f, 0, 1);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = null;
        var pose = new PoseStack().last();
        for (Player player : mc.level.players()) {
            if (!SpiritData.isSpirit(player) || player.distanceToSqr(camera) > 64 * 64) continue;
            var view = player.getData(SpiritData.COMBAT_VIEW);
            if (!view.mode().equals("blade")) continue;
            float elapsed = mc.level.getGameTime() - view.startedAt() + partial;
            boolean ownEye = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            float alpha = opacity(view.stage(), elapsed, ownEye), progress = sweep(view.stage(), elapsed);
            if (alpha <= 0 || progress <= 0) continue;
            if (out == null) out = buffers.getBuffer(SpiritEnergy.ribbon());
            draw(out, pose, player, partial, view.stage(), progress, alpha, camera);
        }
        if (out != null) buffers.endBatch(SpiritEnergy.ribbon());
    }

    private static void draw(VertexConsumer out, PoseStack.Pose pose, Player player, float partial, int stage, float progress,
                             float alpha, Vec3 camera) {
        Vec3 chest = player.getPosition(partial).add(0, player.getBbHeight() * .55, 0);
        float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
        Vec3 forward = BladeArc.facing(yaw), right = new Vec3(-forward.z, 0, forward.x), up = new Vec3(0, 1, 0); // right hand side
        // Left-handed players swing the mirrored clips: mirror the slash sideways, keep its heights.
        Vec3 side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? right.scale(-1) : right;
        boolean overhead = BladeArc.overhead(stage);
        // From Ori's own eye the chest-height fan is nearly edge-on (a thin streak): droop its outer edge and roll the
        // overhead chop aside so the crescent faces the camera. Other viewers keep the true arc.
        var mc = Minecraft.getInstance();
        boolean ownEye = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        double radius = (overhead ? 2.3 : BladeArc.reach(stage) * .8) * (ownEye ? .5 : 1);
        // From the eye a hit-sized arc lies low and edge-on; sweep a closer one just under the eye, across the view.
        if (ownEye) chest = player.getEyePosition(partial).add(0, -.4, 0);
        double from, to;
        if (overhead) { from = Math.toRadians(-25); to = Math.toRadians(135); }
        else {
            double half = BladeArc.halfAngle(stage);
            from = -half * BladeArc.sweep(stage); to = half * BladeArc.sweep(stage);
        }
        float eased = 1 - (1 - progress) * (1 - progress);
        int shown = Math.max(1, Math.round(SEGMENTS * eased));
        for (int layer = 0; layer < 2; layer++) {
            boolean core = layer == 1;
            // Seen from Ori's own eye the band is foreshortened and against bright sky: wider, with a stronger halo.
            double width = (overhead ? .75 : .6) * (core ? .45 : 1.6) * (ownEye ? .8 : 1);
            float strength = alpha * (core ? .9f : ownEye ? .55f : .32f);
            for (int i = 0; i < shown; i++) {
                double a0 = Mth.lerp(i / (double) SEGMENTS, from, to), a1 = Mth.lerp((i + 1) / (double) SEGMENTS, from, to);
                // Crescent: thin at both tips, widest mid-swing; the newest segments are the brightest.
                double w0 = width * Math.pow(Math.sin(Math.PI * i / SEGMENTS), .6), w1 = width * Math.pow(Math.sin(Math.PI * (i + 1) / SEGMENTS), .6);
                float t0 = (float) i / shown, t1 = (float) (i + 1) / shown;
                float alpha0 = strength * (.12f + .88f * t0 * t0), alpha1 = strength * (.12f + .88f * t1 * t1);
                Vec3 d0 = direction(forward, side, up, a0, overhead, ownEye), d1 = direction(forward, side, up, a1, overhead, ownEye);
                SpiritEnergy.strip(out, pose,
                        point(chest, d0, radius - w0 / 2, camera), point(chest, d0, radius + w0 / 2, camera),
                        point(chest, d1, radius - w1 / 2, camera), point(chest, d1, radius + w1 / 2, camera),
                        core ? CORE : HALO, alpha0, alpha1);
            }
        }
    }

    /** Unit direction of the arc at {@code angle}: a tilted horizontal fan, or a vertical overhead chop. */
    private static Vec3 direction(Vec3 forward, Vec3 right, Vec3 up, double angle, boolean overhead, boolean ownEye) {
        if (overhead) return up.scale(Math.cos(angle)).add(forward.scale(Math.sin(angle)))
                .add(right.scale(ownEye ? .45 * Math.cos(angle) : 0)).normalize();
        // Stage 1 cuts high-left to low-right, stage 2 low-right back to high-left: the two cross like Ori's combo.
        double tilt = .32 * -Math.sin(angle) - (ownEye ? .25 : 0);
        return forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).add(up.scale(tilt)).normalize();
    }

    private static Vector3f point(Vec3 chest, Vec3 direction, double radius, Vec3 camera) {
        return chest.add(direction.scale(radius)).subtract(camera).toVector3f();
    }
}
