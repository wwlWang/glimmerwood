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
    /** R95: soft halo around a held tool at the grip (fraction of body height, alpha). */
    public static final float HELD_GLOW_RADIUS = .42f, HELD_GLOW_ALPHA = .24f;
    /** Day light washes additive glow out; scale the aura up by this much in full daylight (skyDarken 0). */
    public static final float BLOOM_DAY_BOOST = 1.35f;
    /** Light the spirit casts on the ground under it: radius (blocks), peak alpha, and how high it still reaches. */
    public static final float GROUND_RADIUS = 2.6f, GROUND_ALPHA = .42f, GROUND_REACH = 4.5f;
    public static final int GROUND_COLOR = 0xB4DAFF;
    /** R96: lantern halos share the spirit batch; loaded-section scans are cached for half a second. */
    public static final int LANTERN_RANGE = 32, LANTERN_SCAN_TICKS = 10, MAX_LANTERNS = 32;
    public static final float LANTERN_RADIUS = .9f, LANTERN_ALPHA = .25f;
    private static final java.util.List<net.minecraft.core.BlockPos> LANTERNS = new java.util.ArrayList<>();
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> lanternLevel = new java.lang.ref.WeakReference<>(null);
    private static long lanternScanAt = Long.MIN_VALUE;
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
        // R95: a held tool glows softly in the cradling hand (grip_l is the player's right hand).
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            if (player.getItemInHand(hand).isEmpty()) continue;
            boolean right = (hand == net.minecraft.world.InteractionHand.MAIN_HAND) == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
            var p = bone(frame, right ? "grip_l" : "grip_r", at, cam);
            if (p != null) SpiritEnergy.billboard(out, pose, p, rotation, h * HELD_GLOW_RADIUS, 0xEAF6FF, HELD_GLOW_ALPHA * b * boost);
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
        groundLight(out, pose, player, at, cam, b, GROUND_RADIUS, true);
    }

    /** R97: share the ground projection with Glimmer, including the local player's first-person pool. */
    private static void groundLight(com.mojang.blaze3d.vertex.VertexConsumer out, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Player player,
                                    net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 cam, float b, float radius, boolean expand) {
        var level = player.level();
        var hit = level.clip(new net.minecraft.world.level.ClipContext(at.add(0, .3, 0), at.subtract(0, GROUND_REACH, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection() != net.minecraft.core.Direction.UP) return;
        float height = (float) (at.y - hit.getLocation().y);
        float fade = Math.clamp(1 - height / GROUND_REACH, 0, 1);
        if (fade <= 0) return;
        var c = new Vector3f((float) (at.x - cam.x), (float) (hit.getLocation().y + .015 - cam.y), (float) (at.z - cam.z));
        float r = radius * (expand ? 1 + .25f * (1 - fade) : 1);
        SpiritEnergy.quad(out, pose, c, new Vector3f(r, 0, 0), new Vector3f(0, 0, r), GROUND_COLOR, GROUND_ALPHA * fade * fade * b);
        SpiritEnergy.quad(out, pose, c, new Vector3f(r * .45f, 0, 0), new Vector3f(0, 0, r * .45f), 0xEAF6FF, GROUND_ALPHA * .8f * fade * fade * b);
    }

    /** R96: palette-filter loaded sections before checking blocks; stop after finding 32 lamps in the sphere. */
    private static void scanLanterns(net.minecraft.client.multiplayer.ClientLevel level, Player player) {
        long now = level.getGameTime();
        if (lanternLevel.get() == level && now >= lanternScanAt && now - lanternScanAt < LANTERN_SCAN_TICKS) return;
        lanternLevel = new java.lang.ref.WeakReference<>(level);
        lanternScanAt = now;
        LANTERNS.clear();
        var centre = player.position();
        int minX = Mth.floor(centre.x - LANTERN_RANGE), maxX = Mth.floor(centre.x + LANTERN_RANGE);
        int minZ = Mth.floor(centre.z - LANTERN_RANGE), maxZ = Mth.floor(centre.z + LANTERN_RANGE);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(centre.y - LANTERN_RANGE));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(centre.y + LANTERN_RANGE));
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            var chunk = level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                if (!section.maybeHas(SpiritAura::isLitLantern)) continue;
                for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++)
                    for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++)
                        for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
                            pos.set(x, y, z);
                            if (pos.distToCenterSqr(centre) <= LANTERN_RANGE * LANTERN_RANGE
                                    && isLitLantern(section.getBlockState(x & 15, y & 15, z & 15))) {
                                LANTERNS.add(pos.immutable());
                                if (LANTERNS.size() >= MAX_LANTERNS) return;
                            }
                        }
            }
        }
    }

    private static boolean isLitLantern(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(cn.wwl.orimod.block.OriBlocks.BASH_ANCHOR.get()) && state.getValue(cn.wwl.orimod.block.SpiritLanternBlock.LIT);
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
        scanLanterns(level, mc.player);
        for (var pos : LANTERNS) {
            // Recheck cached entries so a broken/extinguished lamp vanishes immediately and never loads a chunk.
            if (pos.distToCenterSqr(mc.player.position()) > LANTERN_RANGE * LANTERN_RANGE
                    || !level.hasChunkAt(pos) || !isLitLantern(level.getBlockState(pos))) continue;
            var c = new Vector3f((float) (pos.getX() + .5 - cam.x), (float) (pos.getY() + .5 - cam.y), (float) (pos.getZ() + .5 - cam.z));
            SpiritEnergy.billboard(out, pose, c, rotation, LANTERN_RADIUS, COMPANION_HALO,
                    LANTERN_ALPHA * breath(t + (pos.asLong() & 255) * .037f));
            drew = true;
        }
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
            var orb = CompanionMotion.position(player, partial);
            var orbBlock = net.minecraft.core.BlockPos.containing(orb);
            boolean dark = Math.max(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, orbBlock),
                    level.getBrightness(net.minecraft.world.level.LightLayer.SKY, orbBlock)) < CompanionMotion.DARK_LIGHT_LIMIT;
            // R97: this is intentionally outside the first-person orb guard.
            if (dark) groundLight(out, pose, player, orb, cam, b, CompanionMotion.POOL_RADIUS, false);
            if (!self) {
                var c = new Vector3f((float) (orb.x - cam.x), (float) (orb.y - cam.y), (float) (orb.z - cam.z));
                var state = CompanionMotion.state(player);
                int color = state == null ? CompanionMotion.CALM : state.color(partial);
                boolean combat = state != null && state.combat();
                float pulse = state == null ? 1 : state.pulse(partial);
                float boost = (dark ? CompanionMotion.DARK_BOOST : 1) * pulse;
                SpiritEnergy.billboard(out, pose, c, rotation, .7f, color, COMPANION_OUTER_ALPHA * b * pulse);
                SpiritEnergy.billboard(out, pose, c, rotation, .3f, color, Math.min(1, COMPANION_HALO_ALPHA * b * boost));
                SpiritEnergy.billboard(out, pose, c, rotation, .1f, color,
                        Math.min(1, (COMPANION_CORE_ALPHA + (combat ? CompanionMotion.COMBAT_ALPHA : 0)) * boost));
                double seconds = (level.getGameTime() + partial) * CompanionMotion.STEP_SECONDS;
                // Integrate phase continuously: switching combat changes speed, never jumps petal positions.
                double phase = state == null ? seconds / CompanionMotion.PETAL_PERIOD : state.petalPhase(partial);
                double tilt = Math.toRadians(CompanionMotion.PETAL_TILT);
                for (int i = 0; i < 3; i++) {
                    double angle = (phase + i / 3.0) * Math.PI * 2;
                    float dx = CompanionMotion.PETAL_ORBIT * (float) Math.cos(angle);
                    float dz = CompanionMotion.PETAL_ORBIT * (float) Math.sin(angle);
                    var petal = new Vector3f(c).add(dx, dz * (float) Math.sin(tilt), dz * (float) Math.cos(tilt));
                    SpiritEnergy.billboard(out, pose, petal, rotation, CompanionMotion.PETAL_RADIUS, color, Math.min(1, .75f * boost));
                }
                if (state != null && state.vel.length() > CompanionMotion.RUN_SPEED) {
                    float length = (float) Math.min(state.vel.length() * CompanionMotion.TAIL_SCALE, CompanionMotion.TAIL_MAX);
                    var direction = state.vel.normalize();
                    var along = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
                    var across = new Vector3f(along).cross(new Vector3f(c).negate()).normalize();
                    if (!Float.isFinite(across.x) || across.lengthSquared() < .01f) across = rotation.transform(new Vector3f(1, 0, 0));
                    var centre = new Vector3f(c).sub(new Vector3f(along).mul(length * .5f));
                    SpiritEnergy.quad(out, pose, centre, along.mul(length * .5f), across.mul(CompanionMotion.TAIL_WIDTH), color, .35f * b);
                }
            }
            drew = true;
        }
        if (drew) buffers.endBatch(SpiritEnergy.glow());
    }
}
