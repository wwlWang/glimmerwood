package cn.wwl.orimod.movement;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One selection rule shared by the reticle and server; solids always occlude candidates. */
public final class SpiritTargets {
    public static final double RANGE = 6;
    public static final float MAX_BASH_WIDTH = 1.8f, MAX_BASH_HEIGHT = 3;
    public static final TagKey<EntityType<?>> BASH_IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("orimod", "bash_immune"));
    private SpiritTargets() {}

    public record Target(Entity entity, BlockPos block, Vec3 point, boolean grapple) {
        public String key() { return entity != null ? entity.getUUID().toString() : block.asLong() + ":" + grapple; }
        public AABB bounds() { return entity != null ? entity.getBoundingBox().inflate(0.08) : new AABB(block).deflate(0.18); }
        public boolean valid(Player player) {
            if (entity != null && !eligible(player, entity)) return false;
            if (block != null && (!player.level().hasChunkAt(block) || !player.level().getBlockState(block)
                    .is(grapple ? OriBlocks.GRAPPLE_FLOWER.get() : OriBlocks.BASH_ANCHOR.get()))) return false;
            return player.getEyePosition().distanceToSqr(point) <= RANGE * RANGE && visible(player, point);
        }
    }

    public static Target find(Player player, float yaw, float pitch) {
        Vec3 eye = player.getEyePosition(), view = Vec3.directionFromRotation(pitch, yaw);
        Target best = null;
        double bestScore = -1;
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(RANGE), candidate -> eligible(player, candidate))) {
            Vec3 point = entity.getBoundingBox().getCenter();
            double score = score(eye, view, point);
            if (score > bestScore && visible(player, point)) { best = new Target(entity, null, point, false); bestScore = score; }
        }
        BlockPos origin = BlockPos.containing(eye);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            if (!player.level().hasChunkAt(pos)) continue;
            var block = player.level().getBlockState(pos);
            boolean grapple = block.is(OriBlocks.GRAPPLE_FLOWER.get());
            if (!grapple && !block.is(OriBlocks.BASH_ANCHOR.get())) continue;
            Vec3 point = Vec3.atCenterOf(pos);
            double score = score(eye, view, point);
            if (score > bestScore && visible(player, point)) { best = new Target(null, pos.immutable(), point, grapple); bestScore = score; }
        }
        return best;
    }

    public static boolean eligible(Player player, Entity entity) {
        if (entity == player || !entity.isAlive() || entity.isRemoved() || entity.isInvisible()
                || entity.level() != player.level() || entity.getType().is(BASH_IMMUNE)
                || entity.getBbWidth() > MAX_BASH_WIDTH || entity.getBbHeight() > MAX_BASH_HEIGHT) return false;
        if (entity instanceof FishingHook) return false;
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            // Foreign lodged arrows intentionally remain anchors, with the same per-target cooldown.
            return owner == null || !owner.getUUID().equals(player.getUUID());
        }
        return entity instanceof Enemy && !player.isAlliedTo(entity);
    }

    private static double score(Vec3 eye, Vec3 view, Vec3 point) {
        Vec3 offset = point.subtract(eye);
        double distance = offset.length();
        if (distance > RANGE || distance < 0.1) return -1;
        double alignment = view.dot(offset.scale(1 / distance));
        return alignment >= 0.6 ? alignment * 10 - distance * 0.12 : -1;
    }

    public static boolean visible(Player player, Vec3 point) {
        return player.level().clip(new ClipContext(player.getEyePosition(), point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
}
