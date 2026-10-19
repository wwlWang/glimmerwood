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
    /**
     * R92 bloom, matched to the official references (shots/ref-ori-*.png): a white-hot core that bleeds into a wide,
     * soft pale-blue aura well past the mesh. Layers from the chest outward: radius (x body height) and peak alpha.
     * Each bone glow (head, ear tips, feelers, tail) adds a small soft halo so the aura hugs the silhouette.
     */
    public static final float[] BLOOM_RADIUS = {.4f, .78f, 1.45f, 2.7f}, BLOOM_ALPHA = {.36f, .22f, .11f, .05f};
    public static final int[] BLOOM_COLOR = {0xFFFFFF, 0xE6F3FF, 0xBFE0FF, 0x9CCBFF};
    public static final String[] GLOW_BONES = {"head", "spine", "pelvis", "ear_l_3", "ear_r_3", "ear_l_2", "ear_r_2", "antenna_l_3", "antenna_r_3", "tail_3", "tail_5"};
    public static final float BONE_GLOW_RADIUS = .3f, BONE_GLOW_ALPHA = .2f;
    /** Day light washes additive glow out; scale the aura up by this much in full daylight (skyDarken 0). */
    public static final float BLOOM_DAY_BOOST = 1.35f;
    /** Light the spirit casts on the ground under it: radius (blocks), peak alpha, and how high it still reaches. */
    public static final float GROUND_RADIUS = 2.6f, GROUND_ALPHA = .42f, GROUND_REACH = 4.5f;
    public static final int GROUND_COLOR = 0xB4DAFF;
    private SpiritAura() {}

    /** 0.8..1 slow breathing (same period as the body's breath). */
    public static float breath(float seconds) {
        return .9f + .1f * Mth.sin(seconds * Mth.TWO_PI / cn.wwl.orimod.movement.FeelTuning.BREATH_PERIOD);
    }

    private static void bloom(com.mojang.blaze3d.vertex.VertexConsumer out, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Player player,
                              net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 cam, Vector3f chest,
                              org.joml.Quaternionf rotation, float h, float b, float skyDarken) {
        // skyDarken: 0 at noon, 1 at midnight (Level.getSkyDarken() / 11).
        float boost = Mth.lerp(Math.clamp(skyDarken, 0, 1), BLOOM_DAY_BOOST, 1);
        var frame = cn.wwl.orimod.client.render.SpiritPlayerVisual.frame(player);
        Vector3f centre = chest;
        if (frame != null) {
            var spine = bone(frame, "spine", at, cam);
            if (spine != null) centre = spine;
        }
        for (int i = 0; i < BLOOM_RADIUS.length; i++)
            SpiritEnergy.billboard(out, pose, centre, rotation, h * BLOOM_RADIUS[i], BLOOM_COLOR[i], Math.min(1, BLOOM_ALPHA[i] * b * boost));
        if (frame == null) return;
        for (String name : GLOW_BONES) {
            var p = bone(frame, name, at, cam);
            if (p == null) continue;
            float r = name.startsWith("ear") || name.startsWith("antenna") || name.startsWith("tail") ? BONE_GLOW_RADIUS : BONE_GLOW_RADIUS * 1.6f;
            SpiritEnergy.billboard(out, pose, p, rotation, h * r, 0xF4FAFF, BONE_GLOW_ALPHA * b * boost);
        }
    }

    private static Vector3f bone(cn.wwl.orimod.client.render.SpiritPlayerVisual.Frame frame, String name, net.minecraft.world.phys.Vec3 at,
                                 net.minecraft.world.phys.Vec3 cam) {
        int slot = frame.model().layout().nodeTable().slotOfName(name);
        if (slot < 0) return null;
        var m = frame.boneMatrix(slot, new org.joml.Matrix4f());
        var p = frame.transform().transformPosition(new Vector3f(m.m30(), m.m31(), m.m32()));
        return p.add((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z));
    }

    /** A soft pool of cool light on the first surface below the spirit, fading with height (fake dynamic light). */
    private static void groundLight(com.mojang.blaze3d.vertex.VertexConsumer out, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Player player,
                                    net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 cam, float b) {
        var level = player.level();
        var hit = level.clip(new net.minecraft.world.level.ClipContext(at.add(0, .3, 0), at.subtract(0, GROUND_REACH, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection() != net.minecraft.core.Direction.UP) return;
        float height = (float) (at.y - hit.getLocation().y);
        float fade = Math.clamp(1 - height / GROUND_REACH, 0, 1);
        if (fade <= 0) return;
        var c = new Vector3f((float) (at.x - cam.x), (float) (hit.getLocation().y + .015 - cam.y), (float) (at.z - cam.z));
        float r = GROUND_RADIUS * (1 + .25f * (1 - fade));
        SpiritEnergy.quad(out, pose, c, new Vector3f(r, 0, 0), new Vector3f(0, 0, r), GROUND_COLOR, GROUND_ALPHA * fade * fade * b);
        SpiritEnergy.quad(out, pose, c, new Vector3f(r * .45f, 0, 0), new Vector3f(0, 0, r * .45f), 0xEAF6FF, GROUND_ALPHA * .8f * fade * fade * b);
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
                bloom(out, pose, player, at, cam, chest, rotation, h, b, level.getSkyDarken() / 11f);
                groundLight(out, pose, player, at, cam, b);
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
