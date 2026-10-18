package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class RuinSiteRulesTest {
    @ParameterizedTest @CsvSource({"62,false", "63,true", "100,true", "145,true", "146,false", "-64,false", "319,false"})
    void surfaceHeightBoundaries(int y, boolean ok) { assertEquals(ok, RuinSiteRules.surfaceInRange(y)); }

    @ParameterizedTest @CsvSource({"63,67,true", "63,68,false", "141,145,true", "142,146,false", "62,64,false", "100,100,true"})
    void footprintSlopeBoundaries(int min, int max, boolean ok) { assertEquals(ok, RuinSiteRules.footprintOk(min, max)); }

    @ParameterizedTest @CsvSource({"70,77,true", "70,63,true", "70,78,false", "70,62,false", "64,57,false", "140,145,true", "140,146,false"})
    void approachDeltaBoundaries(int door, int sample, boolean ok) { assertEquals(ok, RuinSiteRules.approachOk(sample, door)); }

    @Test void worldBottomMargin() {
        assertTrue(RuinSiteRules.fitsWorld(-56, 20, -64, 320));
        assertFalse(RuinSiteRules.fitsWorld(-57, 20, -64, 320));
    }

    @Test void worldTopIsExclusive() {
        assertTrue(RuinSiteRules.fitsWorld(299, 20, -64, 320));
        assertFalse(RuinSiteRules.fitsWorld(300, 20, -64, 320));
    }

    @Test void acceptedSurfacesAlwaysFitTheOverworld() {
        for (var kind : RuinKind.values())
            for (int surface = RuinSiteRules.MIN_SURFACE; surface <= RuinSiteRules.MAX_SURFACE; surface++)
                assertTrue(RuinSiteRules.fitsWorld(surface - kind.layout().surfaceY(), kind.layout().size().getY(), -64, 320), kind + " at " + surface);
    }

    @Test void stairsClimbAtMostOneBlockPerRow() {
        for (int door = 60; door <= 150; door++) for (int ground = door - RuinSiteRules.MAX_APPROACH_DELTA; ground <= door + RuinSiteRules.MAX_APPROACH_DELTA; ground++) {
            assertEquals(ground - 1, RuinSiteRules.stepFloor(ground, door, 0), "landing row sits on the ground");
            assertEquals(door - 1, RuinSiteRules.stepFloor(ground, door, 7), "last row meets the door floor");
            for (int i = 1; i <= 7; i++)
                assertTrue(Math.abs(RuinSiteRules.stepFloor(ground, door, i) - RuinSiteRules.stepFloor(ground, door, i - 1)) <= 1, "step taller than a block");
        }
    }

    @Test void deltaEightWouldNeedATwoBlockStep() {
        boolean tall = false;
        for (int i = 1; i <= 7; i++) tall |= Math.abs(RuinSiteRules.stepFloor(62, 70, i) - RuinSiteRules.stepFloor(62, 70, i - 1)) > 1;
        assertTrue(tall, "MAX_APPROACH_DELTA no longer guards a real limit");
    }
}
