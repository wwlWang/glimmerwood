package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuinLocatorTest {
    @ParameterizedTest @ValueSource(ints = {-2000, 0, 2048})
    void denserPlacementSearchCoversTheWholeRadiusWithinItsBound(int coordinate) {
        var origin = new BlockPos(coordinate, 70, coordinate - 100);
        var placement = new RandomSpreadStructurePlacement(6, 3, RandomSpreadType.LINEAR, 19721014);
        var found = RuinLocator.candidates(List.of(placement), 8675321, origin);
        assertFalse(found.truncated());
        assertTrue(found.positions().size() > 1000 && found.positions().size() < RuinLocator.MAX_CANDIDATES);
        assertEquals(found.positions().size(), new HashSet<>(found.positions()).size());
        double previous = -1;
        for (var candidate : found.positions()) {
            double dx = candidate.getMinBlockX() - origin.getX(), dz = candidate.getMinBlockZ() - origin.getZ();
            double distance = dx * dx + dz * dz;
            assertTrue(distance >= previous); previous = distance;
            assertTrue(distance <= 2112d * 2112);
        }
    }
    @Test void oldRecordedNaturalStartsRemainDiscoverableAfterSpacingChanges() {
        var placement = new RandomSpreadStructurePlacement(6, 3, RandomSpreadType.LINEAR, 19721014);
        var found = RuinLocator.candidates(List.of(placement), 8675321, new BlockPos(80, 67, -112));
        // These are real starts recorded by the untouched baseline's vanilla /locate audit.
        assertTrue(found.positions().contains(new ChunkPos(new BlockPos(-688, 0, 880))));
        assertTrue(found.positions().contains(new ChunkPos(new BlockPos(16, 0, 288))));
    }
    @Test void flatWorldHasNoSyntheticLegacyCandidates() {
        assertTrue(RuinLocator.candidates(List.of(), 8675321, BlockPos.ZERO).positions().isEmpty());
    }
    @Test void extremeDatapackDensityIsExplicitlyBounded() {
        var placement = new RandomSpreadStructurePlacement(2, 1, RandomSpreadType.LINEAR, 123);
        var found = RuinLocator.candidates(List.of(placement), 8675321, BlockPos.ZERO);
        assertTrue(found.truncated()); assertEquals(RuinLocator.MAX_CANDIDATES, found.positions().size());
    }
    @Test void realRotatedCreekRemainsEligibleAfterTheFartherCaveStartIsCheckedFirst() {
        // Actual seed 8675321: the creek entrance is 13 blocks away, the cave is 33.
        // Their start chunks have the opposite order, which previously selected the cave.
        var player = new BlockPos(698, 69, 843);
        var caveEntry = new BlockPos(689, 67, 875);
        assertTrue(RuinLocator.couldHaveCloserEntry(player, new ChunkPos(new BlockPos(688, 0, 800)), caveEntry));
        assertFalse(RuinLocator.couldHaveCloserEntry(player, new ChunkPos(new BlockPos(688, 0, 1008)), caveEntry));
    }
    @Test void pruningMarginContainsEveryAuthoredEntranceInEveryOrientation() {
        for (var kind : RuinKind.values()) for (var rotation : Rotation.values()) {
            var placement = RuinPlacement.atMinimum(kind, BlockPos.ZERO, rotation);
            var entry = placement.point(kind.layout().entry());
            assertTrue(Math.hypot(entry.x, entry.z) <= RuinLocator.MAX_ENTRY_OFFSET, kind + "/" + rotation);
        }
    }
}
