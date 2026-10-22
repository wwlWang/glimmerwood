package cn.wwl.orimod.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BladeArcTest {
    private static final Vec3 CHEST = new Vec3(0, 1, 0);

    /** A point {@code distance} away at {@code degrees} off the facing (yaw 0 faces +Z, positive degrees to the left). */
    private static Vec3 at(double degrees, double distance, double dy) {
        double a = Math.toRadians(degrees);
        return CHEST.add(Math.sin(a) * distance, dy, Math.cos(a) * distance);
    }

    @ParameterizedTest @CsvSource({
            "1,0,3.3,true", "1,70,3.0,true", "1,80,3.0,false", "1,0,3.5,false",
            "2,-70,3.0,true", "2,-80,3.0,false",
            "3,85,3.6,true", "3,0,3.75,true", "3,0,3.9,false", "3,120,2.0,false"})
    void fanCoversReachAndAngle(int stage, double degrees, double distance, boolean inside) {
        assertEquals(inside, BladeArc.contains(CHEST, 0, at(degrees, distance, 0), stage));
    }

    @Test
    void widerAndLongerThanThePreviousPoke() {
        // The old test was a 60° half-angle cone of 2.8 blocks; a target at 70° and 3.2 blocks now connects.
        assertTrue(BladeArc.contains(CHEST, 0, at(70, 3.2, 0), 1));
        assertTrue(BladeArc.reach(3) > BladeArc.reach(1));
        assertTrue(BladeArc.halfAngle(3) > BladeArc.halfAngle(1));
    }

    @Test
    void verticalBandAndBodyContact() {
        assertTrue(BladeArc.contains(CHEST, 0, at(0, 2, 1.5), 1));
        assertFalse(BladeArc.contains(CHEST, 0, at(0, 2, 1.7), 1));
        // Pressed against the body counts even behind the facing.
        assertTrue(BladeArc.contains(CHEST, 0, at(180, .3, 0), 1));
        assertFalse(BladeArc.contains(CHEST, 0, at(180, 1, 0), 1));
    }

    @Test
    void facingFollowsMinecraftYaw() {
        assertEquals(1, BladeArc.facing(0).z, 1e-9);
        assertEquals(-1, BladeArc.facing(90).x, 1e-9);
        assertTrue(BladeArc.contains(CHEST, 90, CHEST.add(-3, 0, 0), 1));
        assertFalse(BladeArc.contains(CHEST, 90, CHEST.add(3, 0, 0), 1));
    }

    @Test
    void comboAlternatesAndEndsOverhead() {
        assertEquals(1, BladeArc.sweep(1));
        assertEquals(-1, BladeArc.sweep(2));
        assertTrue(BladeArc.overhead(3));
        assertFalse(BladeArc.overhead(1));
        assertTrue(BladeArc.knockback(3) > BladeArc.knockback(1));
        assertEquals(BladeArc.reach(3), BladeArc.reach(99));
    }

    @Test
    void closestPointClampsIntoTheBox() {
        var box = new AABB(2, 0, 2, 3, 2, 3);
        assertEquals(new Vec3(2, 1, 2), BladeArc.closest(CHEST, box));
    }
}
