package cn.wwl.orimod.fx;

import static org.junit.jupiter.api.Assertions.*;

import cn.wwl.orimod.client.fx.LightOrbGlow;
import org.junit.jupiter.api.Test;

/** R43: Spirit Light orb glow budget. */
class LightOrbGlowTest {
    @Test void restingOrbsLeaveNoTrail() { assertEquals(0, LightOrbGlow.trailCount(0)); assertEquals(0, LightOrbGlow.trailCount(.03)); }
    @Test void pulledOrbsTrailWithinBudget() {
        assertEquals(1, LightOrbGlow.trailCount(.1));
        assertTrue(LightOrbGlow.trailCount(.4) > 1);
        assertEquals(3, LightOrbGlow.trailCount(5));
    }
    @Test void haloBreathes() {
        for (long t = 0; t < 200; t++) { float s = LightOrbGlow.haloSize(t); assertTrue(s >= .35f - 1e-6 && s <= .55f + 1e-6); }
    }
}
