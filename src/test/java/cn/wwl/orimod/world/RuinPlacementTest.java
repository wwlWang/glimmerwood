package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RuinPlacementTest {
    static Stream<Arguments> placements() {
        return Arrays.stream(RuinKind.values()).flatMap(kind -> Arrays.stream(Rotation.values()).map(rotation -> Arguments.of(kind, rotation)));
    }
    @ParameterizedTest @MethodSource("placements")
    void rotatedRouteStaysAlignedWithTemplateCells(RuinKind kind, Rotation rotation) {
        var minimum = new BlockPos(-381, 73, 509);
        var placement = RuinPlacement.atMinimum(kind, minimum, rotation);
        var bounds = placement.blocks(BlockPos.ZERO, kind.layout().size().offset(-1, -1, -1));
        assertEquals(minimum.getX(), bounds.minX()); assertEquals(minimum.getZ(), bounds.minZ());
        for (var point : Stream.concat(Stream.of(kind.layout().safe(), kind.layout().entry()),
                kind.layout().checkpoints().stream().map(RuinKind.Checkpoint::position)).toList()) {
            Vec3 actual = placement.point(point);
            assertEquals(placement.block(BlockPos.containing(point)), BlockPos.containing(actual), "Continuous position rotated into a different block");
            assertTrue(placement.local(actual).distanceTo(point) < 1e-9, "Inverse rotation changed the route position");
            assertTrue(bounds.isInside(BlockPos.containing(actual)), "Route point escaped template bounds");
        }
        for (var clearance : kind.layout().clearances()) {
            var box = placement.clearance(clearance);
            int cells = 0;
            for (var cell : BlockPos.betweenClosed(clearance.from(), clearance.to())) {
                assertTrue(box.contains(Vec3.atCenterOf(placement.block(cell))), "Rotated clearance excluded an original cell");
                cells++;
            }
            assertEquals(cells, box.getXsize() * box.getYsize() * box.getZsize(), 1e-9, "Rotated clearance gained an extra row");
        }
    }
    @Test void clockwiseRotationKeepsHalfBlockCenterOffset() {
        var placement = new RuinPlacement(new BlockPos(100, 64, 200), Rotation.CLOCKWISE_90);
        assertEquals(new BlockPos(96, 66, 203), placement.block(new BlockPos(3, 2, 4)));
        assertEquals(new Vec3(96.5, 66, 203.5), placement.point(new Vec3(3.5, 2, 4.5)));
        assertEquals(new Vec3(96.8, 66, 203.1), placement.point(new Vec3(3.1, 2, 4.2)));
    }
    @Test void shrineFacingUsesTheSameFourRotationsAndOldNbtDefaultsNorth() {
        for (var rotation : Rotation.values()) assertEquals(rotation, RuinPlacement.fromFacing(rotation.rotate(Direction.NORTH)));
        assertEquals(Rotation.NONE, RuinPlacement.readRotation(""));
        assertThrows(IllegalArgumentException.class, () -> RuinPlacement.readRotation("invalid"));
    }
}
