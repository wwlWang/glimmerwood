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
        return blocksTree(level, tree, TREE_MARGIN);
    }

    /** R100: custom large-tree features supply their full horizontal crown radius. */
    public static boolean blocksTree(WorldGenLevel level, BlockPos tree, int margin) {
        // Saplings and bone meal run against ServerLevel, outside this generation-only rule.
        if (!(level instanceof WorldGenRegion region) || !region.getLevel().getServer().getWorldData().worldGenOptions().generateStructures()) return false;
        if(GreatTreeSites.reserved(region.getLevel().getChunkSource().randomState(),tree.getX(),tree.getZ(),margin))return true;
        var center = new ChunkPos(tree);
        for (int x = center.x - START_RADIUS; x <= center.x + START_RADIUS; x++)
            for (int z = center.z - START_RADIUS; z <= center.z + START_RADIUS; z++) {
                if (!region.hasChunk(x, z)) continue;
                // FEATURES already depends on STRUCTURE_STARTS within eight chunks. These
                // saved bounds are immutable here; no block, heightmap or neighbor feature is read.
                var chunk = region.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS);
                for (var start : chunk.getAllStarts().values())
                    if (start.isValid() && (start.getStructure() instanceof RuinStructure || start.getStructure() instanceof GladesOutpostStructure || start.getStructure() instanceof GinsoStructure) && protects(start.getBoundingBox(), tree, margin)) return true;
            }
        return false;
    }

    /** Exact written cell, not a widened feature-origin exclusion radius. */
    public static boolean blocksCreekRock(WorldGenLevel level,BlockPos cell){
        // A registered v1 start is the authority, including manually placed starts.
        // No global generation flag is needed when the saved start is present.
        if(!(level instanceof WorldGenRegion region))return false;
        var center=new ChunkPos(cell);
        // FEATURES guarantees STRUCTURE_STARTS within eight chunks; a rotated
        // 67-block creek plus its skirt can extend beyond the tree guard's four.
        for(int x=center.x-8;x<=center.x+8;x++)for(int z=center.z-8;z<=center.z+8;z++){
            if(!region.hasChunk(x,z))continue;
            for(var start:region.getChunk(x,z,ChunkStatus.STRUCTURE_STARTS).getAllStarts().values()){
                if(!start.isValid()||!(start.getStructure() instanceof RuinStructure))continue;
                for(var piece:start.getPieces())if(piece instanceof RuinPiece ruin&&ruin.kind()==RuinKind.CREEK&&ruin.layoutVersion()==1
                        &&protects(ruin.getBoundingBox(),cell,0))return true;
            }
        }
        return false;
    }

    static boolean protects(BoundingBox bounds, BlockPos tree) {
        return protects(bounds, tree, TREE_MARGIN);
    }
    static boolean protects(BoundingBox bounds, BlockPos tree, int margin) {
        return tree.getX() >= bounds.minX() - margin && tree.getX() <= bounds.maxX() + margin
                && tree.getZ() >= bounds.minZ() - margin && tree.getZ() <= bounds.maxZ() + margin;
    }
}
