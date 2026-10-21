package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** Operator survey: loaded chunks only, section palettes first, exact spherical distance last. Never generates terrain. */
public final class BlightSearch {
    public static final int DEFAULT_RADIUS = 256, MAX_RADIUS = 1024;
    private BlightSearch() {}

    public static Optional<BlockPos> nearest(ServerLevel level, Vec3 origin, int radius) {
        if (radius < 1 || radius > MAX_RADIUS) throw new IllegalArgumentException("Blight search radius must be 1.." + MAX_RADIUS);
        var centre = BlockPos.containing(origin);
        int minX = centre.getX() - radius, maxX = centre.getX() + radius;
        int minZ = centre.getZ() - radius, maxZ = centre.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), centre.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + radius);
        BlockPos best = null;
        double distance = (double) radius * radius;
        var cursor = new BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            // getChunkNow is explicitly non-loading, including for chunks outside the current ticket radius.
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                if (!section.maybeHas(s -> s.is(OriBlocks.GLOOM_CORE))) continue;
                for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++)
                    for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++)
                        for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
                            if (!section.getBlockState(x & 15, y & 15, z & 15).is(OriBlocks.GLOOM_CORE)) continue;
                            cursor.set(x, y, z);
                            double d = cursor.distToCenterSqr(origin);
                            if (d <= distance) { best = cursor.immutable(); distance = d; }
                        }
            }
        }
        return Optional.ofNullable(best);
    }
}
