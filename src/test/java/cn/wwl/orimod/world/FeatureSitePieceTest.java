package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** R114: a declined site retries inside its own chunk, so writes stay in the 3x3 decoration neighbourhood. */
class FeatureSitePieceTest {
    @ParameterizedTest @CsvSource({"-190,122,34", "0,70,0", "15,64,15", "1256,72,-348", "-1872,90,2096", "4,80,4"})
    void retriesStayInTheOriginChunkAndStartAtTheOrigin(int x, int y, int z) {
        var origin = new BlockPos(x, y, z);
        var candidates = FeatureSitePiece.candidates(origin);
        assertEquals(origin, candidates.getFirst());
        assertTrue(candidates.size() >= 2 && candidates.size() <= 3, candidates::toString);
        assertEquals(candidates.size(), candidates.stream().distinct().count());
        for (var at : candidates) {
            assertEquals(new ChunkPos(origin), new ChunkPos(at), at::toString);
            assertEquals(y, at.getY());
        }
        for (int i = 2; i < candidates.size(); i++)
            assertTrue(candidates.get(i - 1).distManhattan(origin) <= candidates.get(i).distManhattan(origin));
    }

    @ParameterizedTest @CsvSource({"CLEARANCE_LOG,true", "CLEARANCE_OTHER,true", "FOOT_BLOCKED,true", "RELIEF,true",
            "TERRAIN,true", "CLEARANCE,true", "RUIN_GUARD,false", "BLIGHT_GUARD,false", "RUIN,false", "SANCTUARY,false",
            "UNLOADED,false", "BUILD_HEIGHT,false"})
    void onlyVegetationDeclinesCutAClearing(String reason, boolean clears) {
        assertEquals(clears, FeatureSitePiece.vegetationMayCause(reason));
    }
}
