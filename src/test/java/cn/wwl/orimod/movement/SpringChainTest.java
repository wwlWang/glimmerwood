package cn.wwl.orimod.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** R92: multi-segment ear/feeler/tail spring chains. */
class SpringChainTest {
    private static SpringChain tail() {
        return new SpringChain(FeelTuning.TAIL_CHAIN_K, FeelTuning.TAIL_CHAIN_C, FeelTuning.TAIL_CHAIN_G, FeelTuning.TAIL_CHAIN_LIMIT, .7f);
    }

    @Test void tipLagsRootAfterAnImpulse() {
        var chain = tail();
        chain.step(1, 0, 0, 1 / 60f);
        int rootPeak = -1, tipPeak = -1; float rootMax = 0, tipMax = 0;
        for (int frame = 0; frame < 120; frame++) {
            chain.step(frame < 6 ? 1 : 0, 0, 0, 1 / 60f);
            float root = Math.abs(chain.angle(0)), tip = Math.abs(chain.angle(chain.segments() - 1));
            if (root > rootMax) { rootMax = root; rootPeak = frame; }
            if (tip > tipMax) { tipMax = tip; tipPeak = frame; }
        }
        assertTrue(tipPeak > rootPeak, "tip peaks after root: " + rootPeak + " vs " + tipPeak);
        assertTrue(tipMax > rootMax, "the tip swings further than the root");
    }

    @Test void settlesBackToRest() {
        var chain = tail();
        chain.kick(4);
        for (int frame = 0; frame < 600; frame++) chain.step(0, 0, 0, 1 / 60f);
        for (int i = 0; i < chain.segments(); i++) assertEquals(0, chain.angle(i), 1e-3);
    }

    @Test void steadyForceCurlsWithinLimits() {
        var chain = tail();
        for (int frame = 0; frame < 600; frame++) chain.step(10, 0, 0, 1 / 60f);
        for (int i = 0; i < chain.segments(); i++) assertTrue(Math.abs(chain.angle(i)) <= FeelTuning.TAIL_CHAIN_LIMIT + 1e-5);
        assertTrue(chain.tip() > 1, "a strong steady force curls the whole tail");
    }

    @Test void frameRateIndependent() {
        var a = tail(); var b = tail();
        for (int i = 0; i < 60; i++) a.step(i < 10 ? 1 : 0, 0, 0, 1 / 60f);
        for (int i = 0; i < 20; i++) b.step(i < 3 ? 1 : 0, 0, 0, 1 / 20f);
        // 10 frames at 60 Hz and 3 at 20 Hz differ slightly in push length (1/6 s vs 3/20 s); the shapes stay close.
        assertEquals(a.tip(), b.tip(), .08);
    }

    @Test void idleSwayTravelsDownTheChain() {
        var chain = new SpringChain(FeelTuning.EAR_CHAIN_K, FeelTuning.EAR_CHAIN_C, FeelTuning.EAR_CHAIN_G, FeelTuning.EAR_CHAIN_LIMIT, .9f);
        float max = 0;
        for (int i = 0; i < 600; i++) { chain.step(0, FeelTuning.SEC_IDLE_SWAY, FeelTuning.SEC_IDLE_RATE, 1 / 60f); max = Math.max(max, Math.abs(chain.tip())); }
        assertTrue(max > .01f && max < .5f, "gentle idle sway: " + max);
    }

    @Test void rejectsMismatchedSegments() {
        assertThrows(IllegalArgumentException.class, () -> new SpringChain(new float[] {1, 2}, new float[] {1}, new float[] {1, 1}, 1, 0));
    }

    @Test void tipOvershootIsVisible() {
        var chain = new SpringChain(FeelTuning.EAR_CHAIN_K, FeelTuning.EAR_CHAIN_C, FeelTuning.EAR_CHAIN_G, FeelTuning.EAR_CHAIN_LIMIT, .9f);
        chain.kick(FeelTuning.SEC_LAND_KICK);
        float tipPeak = 0, rootPeak = 0; int tipAt = -1, rootAt = -1;
        for (int i = 0; i < 90; i++) {
            chain.step(0, 0, 0, 1 / 60f);
            float root = Math.abs(chain.angle(0)), tip = Math.abs(chain.angle(2));
            if (root > rootPeak) { rootPeak = root; rootAt = i; }
            if (tip > tipPeak) { tipPeak = tip; tipAt = i; }
        }
        assertTrue(tipAt > rootAt, "ear tip lags root after land kick");
        assertTrue(tipPeak > .2f, "land flop too weak: " + tipPeak);
    }

    @Test void restPoseTables() {
        assertEquals(3, FeelTuning.EAR_REST_LIFT.length);
        assertEquals(5, FeelTuning.TAIL_REST_LIFT.length);
        assertEquals(3, FeelTuning.FEELER_REST_PITCH.length);
        assertTrue(FeelTuning.EAR_REST_LIFT[0] > .3f, "ears rest swept up into a V");
    }
}
