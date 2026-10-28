package cn.wwl.orimod.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** R54: the corruption front and the escape rule. */
class EscapeSequenceTest {
    @Test void eightSecondWarningKeepsFrontAtOrigin() {
        assertEquals(3, EscapeSequence.frontRadius(159), 1e-9);
        assertEquals(3, EscapeSequence.frontRadius(160), 1e-9);
        assertEquals(7, EscapeSequence.frontRadius(180), 1e-9);
        assertEquals(11, EscapeSequence.frontRadius(200), 1e-9);
        assertFalse(EscapeSequence.caught(0, 159), "Warning must not damage a player still at the altar");
        assertTrue(EscapeSequence.caught(0, 160));
    }
    @Test void tunedDamageKeepsOtherDifficultyParameters() {
        assertEquals(2, EscapeSequence.FRONT_DAMAGE);
        assertEquals(10, EscapeSequence.HURT_INTERVAL);
        assertEquals(.2, EscapeSequence.FRONT_SPEED);
        assertEquals(1200, EscapeSequence.LIMIT_TICKS);
        assertEquals(12, EscapeSequence.ESCAPE_DISTANCE);
    }
    @Test void frontGrowsSteadilyButCanBeOutrun() {
        assertEquals(EscapeSequence.START_RADIUS, EscapeSequence.frontRadius(0), 1e-9);
        assertTrue(EscapeSequence.frontRadius(160 + 100) > EscapeSequence.frontRadius(160 + 50));
        assertTrue(EscapeSequence.FRONT_SPEED < .28, "a sprinting player (~.28 b/t) must outrun the front");
    }
    @Test void escapingNeedsOpenSkyAndDistance() {
        assertFalse(EscapeSequence.escaped(30, false));
        assertFalse(EscapeSequence.escaped(5, true));
        assertTrue(EscapeSequence.escaped(EscapeSequence.ESCAPE_DISTANCE, true));
    }
    @Test void caughtInsideTheFront() {
        assertTrue(EscapeSequence.caught(2, 160));
        assertFalse(EscapeSequence.caught(10, 160));
        assertTrue(EscapeSequence.caught(10, 160 + 40));
    }
}
