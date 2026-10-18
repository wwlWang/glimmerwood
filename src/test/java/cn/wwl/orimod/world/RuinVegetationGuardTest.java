package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuinVegetationGuardTest {
    @Test void treesBesideTheReproducedCreekCannotReachItsDoorway() {
        var bounds = new BoundingBox(688, 64, 800, 708, 84, 843);
        assertTrue(RuinVegetationGuard.protects(bounds, new BlockPos(711, 68, 833)));
        assertTrue(RuinVegetationGuard.protects(bounds, new BlockPos(698, 68, 843)));
        assertFalse(RuinVegetationGuard.protects(bounds, new BlockPos(721, 68, 833)));
        assertFalse(RuinVegetationGuard.protects(bounds, new BlockPos(675, 68, 833)));
        assertFalse(RuinVegetationGuard.protects(bounds, new BlockPos(698, 68, 856)));
        assertFalse(RuinVegetationGuard.protects(bounds, new BlockPos(698, 68, 787)));
    }
    @Test void lookupRadiusCoversEveryRotatedFootprintAndApproachMargin() {
        for (var kind : RuinKind.values()) for (var rotation : Rotation.values()) {
            var placement = RuinPlacement.atMinimum(kind, BlockPos.ZERO, rotation);
            var bounds = placement.blocks(BlockPos.ZERO, kind.layout().size().offset(-1, -1, -1));
            var door = RuinApproachPiece.doorway(kind, placement);
            var approach = door.blocks(new BlockPos(-2, 0, -9), new BlockPos(2, 0, -1));
            int margin = RuinVegetationGuard.TREE_MARGIN;
            for (int x : new int[]{Math.min(bounds.minX(), approach.minX()) - margin, Math.max(bounds.maxX(), approach.maxX()) + margin})
                for (int z : new int[]{Math.min(bounds.minZ(), approach.minZ()) - margin, Math.max(bounds.maxZ(), approach.maxZ()) + margin})
                    assertTrue(new ChunkPos(new BlockPos(x, 0, z)).getChessboardDistance(0, 0) <= RuinVegetationGuard.START_RADIUS, kind + "/" + rotation);
        }
    }
}
