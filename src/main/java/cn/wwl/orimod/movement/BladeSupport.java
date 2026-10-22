package cn.wwl.orimod.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.List;
import java.util.function.Function;

/** Conservative same-height footing for a forced step; ordinary walking/jumping is never restricted. */
public final class BladeSupport {
    private static final double SAMPLE = .1, HEIGHT_EPSILON = .0625;
    private BladeSupport() { }

    /** Pure geometry. Check the centre and four inset sole corners, including both endpoints of the path. */
    public static boolean supported(AABB body, Vec3 motion, Function<Vec3, List<AABB>> surfaces) {
        int steps = Math.max(1, (int) Math.ceil(motion.horizontalDistance() / SAMPLE));
        double halfX = body.getXsize() * .4, halfZ = body.getZsize() * .4;
        for (int i = 0; i <= steps; i++) {
            Vec3 foot = new Vec3((body.minX + body.maxX) / 2, body.minY, (body.minZ + body.maxZ) / 2)
                    .add(motion.scale((double) i / steps));
            for (int corner = 0; corner < 5; corner++) {
                Vec3 at = corner == 0 ? foot : foot.add((corner <= 2 ? -1 : 1) * halfX, 0, (corner % 2 == 0 ? -1 : 1) * halfZ);
                boolean grounded = surfaces.apply(at).stream().anyMatch(box ->
                        Math.abs(box.maxY - at.y) <= HEIGHT_EPSILON && box.getYsize() > 0
                        && at.x >= box.minX - 1e-7 && at.x <= box.maxX + 1e-7
                        && at.z >= box.minZ - 1e-7 && at.z <= box.maxZ + 1e-7);
                if (!grounded) return false;
            }
        }
        return true;
    }

    /** Both client prediction and GameTests use the same geometry with actual collision shapes. No chunk loading. */
    public static boolean supported(Player player, Vec3 motion) {
        var level = player.level();
        if (!level.noCollision(player, player.getBoundingBox().deflate(.01).expandTowards(motion))) return false;
        return supported(player.getBoundingBox(), motion, at -> {
            var boxes = new java.util.ArrayList<AABB>();
            BlockPos below = BlockPos.containing(at.x, at.y - HEIGHT_EPSILON, at.z);
            for (int y = -1; y <= 1; y++) {
                var pos = below.offset(0, y, 0);
                if (!level.hasChunkAt(pos)) continue;
                for (var box : level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.of(player)).toAabbs())
                    boxes.add(box.move(pos));
            }
            return boxes;
        });
    }
}
