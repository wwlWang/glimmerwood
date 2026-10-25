package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class KeystoneDoorPartTest {
    @Test void everyPartResolvesOneThreeByThreeDoorAtNegativeCoordinatesAndChunkEdges() {
        for (var facing : Direction.Plane.HORIZONTAL) for (var origin : new BlockPos[]{new BlockPos(-16, -30, -1), new BlockPos(15, 60, 16)}) {
            var positions = new HashSet<BlockPos>();
            for (var part : KeystoneDoorPart.PLACED) {
                var position = part.position(origin, facing);
                assertTrue(positions.add(position), "Two parts occupy the same cell");
                assertEquals(origin, part.origin(position, facing));
            }
            assertEquals(9, positions.size());
            var shape = KeystoneDoorShape.of(positions.stream().toList(), 0);
            assertEquals(3, shape.width()); assertEquals(3, shape.height());
            assertEquals(9, shape.bounds().getXsize() * shape.bounds().getYsize() * shape.bounds().getZsize());
            assertFalse(KeystoneDoorPart.PLACED.contains(KeystoneDoorPart.LEGACY));
        }
    }

    @Test void mirroringPreservesOwnershipForBothAxesAndAllFacings() {
        for (var facing : Direction.Plane.HORIZONTAL) for (var mirror : new Mirror[]{Mirror.LEFT_RIGHT, Mirror.FRONT_BACK})
            for (var part : KeystoneDoorPart.PLACED) {
                var before = part.position(BlockPos.ZERO, facing);
                var expected = mirror == Mirror.LEFT_RIGHT ? new BlockPos(before.getX(), before.getY(), -before.getZ())
                        : new BlockPos(-before.getX(), before.getY(), before.getZ());
                assertEquals(expected, part.mirrored().position(BlockPos.ZERO, mirror.mirror(facing)));
                assertEquals(part, part.mirrored().mirrored());
            }
        assertEquals(KeystoneDoorPart.LEGACY, KeystoneDoorPart.LEGACY.mirrored());
    }
}
