package cn.wwl.orimod.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** R35: Spirit Light orb counts and magnet radius. */
class SpiritLightOrbTest {
    @Test void orbsScaleWithEnemyHealth() {
        assertEquals(2, SpiritProgression.lightFor(20));   // zombie keeps the old 2
        assertEquals(1, SpiritProgression.lightFor(4));
        assertEquals(4, SpiritProgression.lightFor(40));   // enderman
        assertEquals(8, SpiritProgression.lightFor(300));
        assertEquals(1, SpiritProgression.lightFor(Float.NaN));
    }

    @Test void spiritFormPullsNearbyAndGatheringFurther() {
        assertEquals(0, SpiritProgression.magnetRadius(false, false));
        assertEquals(2.5, SpiritProgression.magnetRadius(true, false));
        assertEquals(4, SpiritProgression.magnetRadius(true, true));
    }
}
