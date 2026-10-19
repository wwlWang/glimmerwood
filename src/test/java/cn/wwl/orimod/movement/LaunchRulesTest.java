package cn.wwl.orimod.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** R38: Launch direction and gating. */
class LaunchRulesTest {
    @Test void flatViewIsLiftedToTheMinimumRise() {
        var v = MovementRules.launchVelocity(0, 0, true);
        assertEquals(MovementRules.LAUNCH_GROUND_SPEED, v.length(), 1e-9);
        assertEquals(MovementRules.LAUNCH_MIN_UP * MovementRules.LAUNCH_GROUND_SPEED, v.y, 1e-9);
        assertTrue(v.z > 0, "keeps the facing (yaw 0 = +Z)");
    }

    @Test void steepViewIsKeptAndDownIsStraightUp() {
        var up = MovementRules.launchVelocity(90, -80, false);
        assertEquals(MovementRules.LAUNCH_AIR_SPEED, up.length(), 1e-9);
        assertTrue(up.y > .9 * MovementRules.LAUNCH_AIR_SPEED);
        var down = MovementRules.launchVelocity(0, 90, true);
        assertTrue(down.y >= MovementRules.LAUNCH_MIN_UP * MovementRules.LAUNCH_GROUND_SPEED - 1e-9, "never launches downward");
    }

    @Test void gating() {
        assertFalse(MovementRules.launchAllowed(false, true, 99, false));
        assertFalse(MovementRules.launchAllowed(true, true, MovementRules.LAUNCH_CHARGE_TICKS - 1, false));
        assertTrue(MovementRules.launchAllowed(true, true, MovementRules.LAUNCH_CHARGE_TICKS, true));
        assertTrue(MovementRules.launchAllowed(true, false, 0, false));
        assertFalse(MovementRules.launchAllowed(true, false, 0, true));
    }
}
