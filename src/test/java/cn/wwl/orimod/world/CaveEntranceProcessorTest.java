package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** R114: the buried cave altar keeps only its doorway and stair headroom from the old open trench. */
class CaveEntranceProcessorTest {
    @ParameterizedTest @CsvSource({
            "11,13,0,true",   // doorway landing
            "8,18,1,true",    // doorway edge, top of the old trench
            "11,15,2,true",   // first stair: floor 11, headroom 12..15
            "11,16,2,false",  // above that headroom the hillside stays
            "6,12,5,false",   // trench side walls are no longer cleared
            "16,18,13,false",
            "11,5,13,true",   // bottom stair headroom (floor 1, air 2..5)
            "3,5,20,true",    // outside the trench: template air untouched
            "11,11,20,true"})
    void trenchCells(int x, int y, int z, boolean kept) { assertEquals(kept, CaveEntranceProcessor.keepsAir(x, y, z)); }

    @Test void buriedDoorwayStaysReachableFromTheApproach() {
        // Lowest sample is CAVE_COVER above the door; the highest is at most MAX_SLOPE above that.
        assertTrue(RuinSiteRules.CAVE_COVER + RuinSiteRules.MAX_SLOPE <= RuinSiteRules.MAX_APPROACH_DELTA);
        assertTrue(RuinSiteRules.CAVE_COVER >= 2, "the roof plate needs at least two blocks of natural cover");
    }

    @Test void routeAndClearancesNeverLoseTheirAir() {
        var layout = RuinKind.CAVE.layout();
        for (var clearance : layout.clearances())
            for (int x = clearance.from().getX(); x <= clearance.to().getX(); x++)
                for (int y = clearance.from().getY(); y <= clearance.to().getY(); y++)
                    for (int z = clearance.from().getZ(); z <= clearance.to().getZ(); z++)
                        assertTrue(CaveEntranceProcessor.keepsAir(x, y, z), "clearance " + x + "," + y + "," + z);
        var entry = layout.entry();
        for (int y = 0; y < 2; y++)
            assertTrue(CaveEntranceProcessor.keepsAir((int)Math.floor(entry.x), (int)Math.floor(entry.y) + y, (int)Math.floor(entry.z)), "entry headroom");
    }
}
