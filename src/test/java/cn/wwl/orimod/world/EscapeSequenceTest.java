package cn.wwl.orimod.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** R54: the corruption front and the escape rule. */
class EscapeSequenceTest {
    @Test void frontGrowsSteadilyButCanBeOutrun() {
        assertEquals(EscapeSequence.START_RADIUS, EscapeSequence.frontRadius(0), 1e-9);
        assertTrue(EscapeSequence.frontRadius(100) > EscapeSequence.frontRadius(50));
        assertTrue(EscapeSequence.FRONT_SPEED < .28, "a sprinting player (~.28 b/t) must outrun the front");
    }
    @Test void escapingNeedsOpenSkyAndDistance() {
        assertFalse(EscapeSequence.escaped(30, false));
        assertFalse(EscapeSequence.escaped(5, true));
        assertTrue(EscapeSequence.escaped(EscapeSequence.ESCAPE_DISTANCE, true));
    }
    @Test void caughtInsideTheFront() {
        assertTrue(EscapeSequence.caught(2, 0));
        assertFalse(EscapeSequence.caught(10, 0));
        assertTrue(EscapeSequence.caught(10, 40));
    }
}
