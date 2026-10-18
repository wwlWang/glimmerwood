package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Keep natural tree crowns out of ruins even when a neighboring chunk decorates later. */
public final class RuinVegetationGuard {
    static final int TREE_MARGIN = 12, START_RADIUS = 4;
    private RuinVegetationGuard() {}

    public static boolean blocksTree(WorldGenLevel level, BlockPos tree) {
        // Saplings and bone meal run against ServerLevel, outside this generation-only rule.
        if (!(level instanceof WorldGenRegion region) || !region.getLevel().getServer().getWorldData().worldGenOptions().generateStructures()) return false;
        var center = new ChunkPos(tree);
        for (int x = center.x - START_RADIUS; x <= center.x + START_RADIUS; x++)
            for (int z = center.z - START_RADIUS; z <= center.z + START_RADIUS; z++) {
                if (!region.hasChunk(x, z)) continue;
                // FEATURES already depends on STRUCTURE_STARTS within eight chunks. These
                // saved bounds are immutable here; no block, heightmap or neighbor feature is read.
                var chunk = region.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS);
                for (var start : chunk.getAllStarts().values())
                    if (start.isValid() && start.getStructure() instanceof RuinStructure && protects(start.getBoundingBox(), tree)) return true;
            }
        return false;
    }

    static boolean protects(BoundingBox bounds, BlockPos tree) {
        return tree.getX() >= bounds.minX() - TREE_MARGIN && tree.getX() <= bounds.maxX() + TREE_MARGIN
                && tree.getZ() >= bounds.minZ() - TREE_MARGIN && tree.getZ() <= bounds.maxZ() + TREE_MARGIN;
    }
}
