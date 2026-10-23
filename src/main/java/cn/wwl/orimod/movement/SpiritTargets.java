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
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(RANGE), candidate -> eligible(player, candidate))) {
            Vec3 point = entity.getBoundingBox().getCenter();
            if (!available(player,entity.getUUID().toString())) continue;
            if (preferred(eye, view, point, best == null ? null : best.point()) && visible(player, point)) best = new Target(entity, null, point, false);
        }
        BlockPos origin = BlockPos.containing(eye);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            if (!player.level().hasChunkAt(pos)) continue;
            var block = player.level().getBlockState(pos);
            boolean grapple = block.is(OriBlocks.GRAPPLE_FLOWER.get());
            if (!grapple && !block.is(OriBlocks.BASH_ANCHOR.get())) continue;
            Vec3 point = Vec3.atCenterOf(pos);
            if (!available(player,pos.asLong()+":"+grapple)) continue;
            // Grapple is still an aimed ability; only Bash gains the all-around proximity pick.
            if (grapple && view.dot(point.subtract(eye).normalize()) < .6) continue;
            if (preferred(eye, view, point, best == null ? null : best.point()) && visible(player, point)) best = new Target(null, pos.immutable(), point, grapple);
        }
        return best;
    }

    public static boolean eligible(Player player, Entity entity) {
        if (entity == player || !entity.isAlive() || entity.isRemoved() || entity.isInvisible()
                || entity.level() != player.level() || entity.getType().is(BASH_IMMUNE)
                || entity.getBbWidth() > MAX_BASH_WIDTH || entity.getBbHeight() > MAX_BASH_HEIGHT) return false;
        if (entity instanceof FishingHook) return false;
        // R39: a flying Light Burst is a Bash anchor for everyone, including its thrower.
        if (entity instanceof cn.wwl.orimod.combat.LightBurst) return true;
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            // Foreign lodged arrows intentionally remain anchors, with the same per-target cooldown.
            return owner == null || !owner.getUUID().equals(player.getUUID());
        }
        return entity instanceof Enemy && !player.isAlliedTo(entity);
    }

    /** A nearby target behind remains available, but forward chaining wins at comparable distances. */
    public static double score(Vec3 eye, Vec3 view, Vec3 point) {
        Vec3 offset=point.subtract(eye);double distance=offset.length();
        if(distance>RANGE || distance<.1)return Double.POSITIVE_INFINITY;
        return distance*(1.25-.45*view.dot(offset.scale(1/distance)));
    }
    public static boolean preferred(Vec3 eye, Vec3 view, Vec3 point, Vec3 previous) {
        return score(eye,view,point)<(previous==null?Double.POSITIVE_INFINITY:score(eye,view,previous));
    }
    private static boolean available(Player player,String key) {
        var s=player.getData(cn.wwl.orimod.spirit.SpiritData.MOVEMENT);long now=player.level().getGameTime();
        if(!s.recentBash.isEmpty() && (now>=s.recentBashUntil || now>=s.groundLockUntil && SpiritMovement.grounded(player))){
            s.targetCooldowns.remove(s.recentBash);s.recentBash="";
        }
        return s.targetCooldowns.getOrDefault(key,Long.MIN_VALUE)<=now;
    }

    public static boolean visible(Player player, Vec3 point) {
        return player.level().clip(new ClipContext(player.getEyePosition(), point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
}
