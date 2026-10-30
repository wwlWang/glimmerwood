package cn.wwl.orimod.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlamRulesTest {
    @Test void initialSpeedIsPreservedAndFastFallsAreCapped() {
        assertEquals(-1.2, SlamRules.initialSpeed(.42));
        assertEquals(-2, SlamRules.initialSpeed(-2));
        assertEquals(SlamRules.TERMINAL_SPEED, SlamRules.initialSpeed(-20));
    }
    @Test void hammerAndStompAccelerateMonotonicallyToSameTerminalSpeed() {
        double y = SlamRules.START_SPEED;
        for (int tick = 0; tick < 100; tick++) {
            double next = HammerSlam.velocity(new Vec3(.2, y, .3)).y;
            assertTrue(next < y || y == SlamRules.TERMINAL_SPEED);
            assertEquals(cn.wwl.orimod.movement.MovementRules.stompVelocity(y).y, next);
            assertTrue(next >= SlamRules.TERMINAL_SPEED);
            y = next;
        }
        assertEquals(SlamRules.TERMINAL_SPEED, y);
        assertEquals(2.75, SlamRules.TERMINAL_SPEED / SlamRules.START_SPEED, 1e-9);
    }
    @Test void heightBoostIsProgressiveAndCapped() {
        assertEquals(1, SlamRules.strength(0));
        assertTrue(SlamRules.strength(12) > SlamRules.strength(4));
        assertEquals(1.75, SlamRules.strength(24));
        assertEquals(1.75, SlamRules.strength(1000));
        assertEquals(1, SlamRules.strength(-10));
        assertEquals(1, SlamRules.strength(Double.NaN));
        assertEquals(1.375, SlamRules.radiusScale(100));
    }
}
