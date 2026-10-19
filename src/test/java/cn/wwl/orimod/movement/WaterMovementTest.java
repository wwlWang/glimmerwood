package cn.wwl.orimod.movement;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** R56: Water Dash and swim boost rules. */
class WaterMovementTest {
    @Test void dashFollowsTheLookDirection() {
        var v = WaterMovement.dashVelocity(new Vec3(0, -1, 1));
        assertEquals(WaterMovement.DASH_SPEED, v.length(), 1e-9);
        assertTrue(v.y < 0 && v.z > 0);
    }
    @Test void swimBoostIsCapped() {
        var look = new Vec3(0, 0, 1);
        assertEquals(new Vec3(0, 0, .1), WaterMovement.swimVelocity(new Vec3(0, 0, .1), look, false));
        assertTrue(WaterMovement.swimVelocity(new Vec3(0, 0, .1), look, true).z > .1);
        var fast = new Vec3(0, 0, WaterMovement.SWIM_MAX);
        assertEquals(fast, WaterMovement.swimVelocity(fast, look, true));
    }
}
