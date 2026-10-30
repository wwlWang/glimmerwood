package cn.wwl.orimod.movement;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** R55: Climb rules. */
class ClimbRulesTest {
    @Test void contactWhitelistOnlyRestrictsClimb() {
        assertFalse(MovementRules.climbing(true, 1, false));
        assertTrue(MovementRules.climbing(true, 1, true));
        assertFalse(MovementRules.climbing(false, 1, true));
        assertEquals(MoveMode.WALL_JUMP, MovementRules.jumpMode(false, 20, 0, false, true, true, true, false));
    }
    @Test void climbsOnlyWithAbilityAndForwardInput() {
        assertTrue(MovementRules.climbing(true, 1));
        assertFalse(MovementRules.climbing(false, 1));
        assertFalse(MovementRules.climbing(true, 0));
        assertFalse(MovementRules.climbing(true, -1));
    }
    @Test void climbRisesSlowerThanAJump() {
        var v = MovementRules.climbVelocity(new Vec3(.1, -.3, 0));
        assertEquals(MovementRules.CLIMB_SPEED, v.y, 1e-9);
        assertEquals(.1, v.x, 1e-9);
        assertTrue(MovementRules.CLIMB_SPEED > 0 && MovementRules.CLIMB_SPEED < .42);
        assertEquals(cn.wwl.orimod.progression.Ability.DASH, cn.wwl.orimod.progression.Ability.CLIMB.prerequisite);
    }

    // ---- R94 mantle ----
    @Test void mantlePopClearsTheLipWithoutBecomingAJump() {
        for (double rise : new double[] {.2, .5, .8, 1.0, MovementRules.MANTLE_REACH}) {
            double vy = MovementRules.mantleLaunch(rise);
            assertTrue(MovementRules.peak(vy) >= rise + MovementRules.MANTLE_CLEARANCE - 1e-6, "clears " + rise);
            assertTrue(MovementRules.peak(vy) < rise + MovementRules.MANTLE_CLEARANCE + .1, "no overshoot " + rise);
        }
        assertTrue(MovementRules.mantleLaunch(.5) < MovementRules.mantleLaunch(1.0));
        assertTrue(MovementRules.mantleLaunch(MovementRules.MANTLE_REACH) < .75);
    }
    @Test void mantleReachWindow() {
        assertTrue(MovementRules.mantleReach(.5));
        assertTrue(MovementRules.mantleReach(MovementRules.MANTLE_REACH));
        assertFalse(MovementRules.mantleReach(MovementRules.MANTLE_REACH + .1));
        assertFalse(MovementRules.mantleReach(.05));
        assertTrue(MovementRules.MANTLE_FORWARD > MovementRules.MANTLE_HOLD && MovementRules.MANTLE_MAX_TICKS >= 8);
    }
    @Test void peakMatchesVanillaJump() {
        // vanilla jump .42 peaks at ~1.25 blocks
        assertEquals(1.25, MovementRules.peak(.42), .03);
    }
}
