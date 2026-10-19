package cn.wwl.orimod.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** R33: projectiles fired back by Bash leave at least as fast as a drawn arrow, and never absurdly fast. */
class BashRedirectTest {
    @Test void slowProjectilesAreBoostedToTheMinimum() {
        assertEquals(BashSession.REDIRECT_MIN_SPEED, BashSession.redirectSpeed(0.2), 1e-9);
        assertEquals(BashSession.REDIRECT_MIN_SPEED, BashSession.redirectSpeed(0), 1e-9);
    }

    @Test void fastProjectilesKeepAGainButAreCapped() {
        assertEquals(2.4, BashSession.redirectSpeed(2.0), 1e-9);
        assertEquals(4, BashSession.redirectSpeed(10), 1e-9);
    }
}
