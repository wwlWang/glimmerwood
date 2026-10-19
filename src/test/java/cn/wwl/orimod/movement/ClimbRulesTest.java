package cn.wwl.orimod.movement;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** R55: Climb rules. */
class ClimbRulesTest {
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
}
