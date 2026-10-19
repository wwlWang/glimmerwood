package cn.wwl.orimod.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** R40: updraft lift and triple-jump accounting. */
class UpdraftRulesTest {
    @Test void updraftAcceleratesUpToTheCap() {
        assertEquals(MovementRules.GLIDE_FALL_SPEED + MovementRules.UPDRAFT_ACCEL, MovementRules.updraftVelocity(-1), 1e-9);
        assertEquals(.1 + MovementRules.UPDRAFT_ACCEL, MovementRules.updraftVelocity(.1), 1e-9);
        assertEquals(MovementRules.UPDRAFT_MAX, MovementRules.updraftVelocity(.49), 1e-9);
    }

    @Test void tripleJumpNeedsTheShard() {
        assertFalse(MovementRules.airJumpsSpent(false, false, false));
        assertTrue(MovementRules.airJumpsSpent(true, false, false));
        assertFalse(MovementRules.airJumpsSpent(true, true, false));
        assertTrue(MovementRules.airJumpsSpent(true, true, true));
    }
}
