package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static cn.wwl.orimod.movement.FeelTuning.*;
import static org.junit.jupiter.api.Assertions.*;

class FeelTuningTest {
    static final Vec3 X = new Vec3(1, 0, 0);

    static int ticksTo(Vec3 start, Vec3 wish, boolean ground, double fraction) {
        Vec3 v = start;
        for (int t = 1; t <= 60; t++) { v = horizontal(v, wish, ground, 1, 1); if (Math.abs(v.length() - TOP_SPEED * fraction) < 1e-6 || (fraction == 0 ? v.length() < 1e-6 : v.length() >= TOP_SPEED * fraction - 1e-6)) return t; }
        return 99;
    }

    @Test void groundReachesTopSpeedFastAndStopsFaster() {
        assertTrue(ticksTo(Vec3.ZERO, X, true, 1) <= 3, "accel ticks " + ticksTo(Vec3.ZERO, X, true, 1));
        Vec3 v = X.scale(TOP_SPEED); int t = 0;
        while (v.length() > 1e-6 && t < 20) { v = horizontal(v, Vec3.ZERO, true, 1, 1); t++; }
        assertTrue(t <= 2, "stop ticks " + t);
    }

    @Test void reversingBrakesAtDecelRate() {
        Vec3 v = horizontal(X.scale(TOP_SPEED), X.scale(-1), true, 1, 1);
        assertEquals(TOP_SPEED - GROUND_DECEL, v.x, 1e-9);
    }

    @Test void airKeepsMomentumWithoutInput() {
        Vec3 v = horizontal(X.scale(TOP_SPEED), Vec3.ZERO, false, 1, 1);
        assertEquals(TOP_SPEED - AIR_DRAG, v.x, 1e-9);
    }

    @Test void overspeedDecaysSlowlyToTop() {
        Vec3 v = X.scale(DASH_EXIT_SPEED); int t = 0;
        while (v.length() > TOP_SPEED + 1e-6 && t < 60) { v = horizontal(v, X, true, 1, 1); t++; }
        assertTrue(t >= 3 && t <= 6, "carry ticks " + t);
        assertEquals(TOP_SPEED, v.length(), 1e-6);
    }

    @Test void releaseCutsTheRise() {
        assertEquals(.42 * JUMP_CUT, vertical(.42, false, true), 1e-9);
        assertEquals(.05, vertical(.05, false, true), 1e-9 + GRAVITY);   // too slow to cut: just gravity rules
    }

    @Test void gravityScalesByPhase() {
        // effective gravity = vy - (vertical(vy) - GRAVITY)
        assertEquals(GRAVITY * RISE_GRAVITY_SCALE, .3 - (vertical(.3, true, false) - GRAVITY), 1e-9);
        assertEquals(GRAVITY * APEX_GRAVITY_SCALE, .02 - (vertical(.02, true, false) - GRAVITY), 1e-9);
        assertEquals(GRAVITY * FALL_GRAVITY_SCALE, -.3 - (vertical(-.3, true, false) - GRAVITY), 1e-9);
        assertEquals(GRAVITY, .3 - (vertical(.3, false, false) - GRAVITY), 1e-9);
    }

    @Test void heldJumpGoesHigherThanTappedAndFallsAreCapped() {
        assertTrue(apex(true) > 1.45, "held " + apex(true));
        assertTrue(apex(false) < apex(true) * .6 && apex(false) >= 1.0, "tap " + apex(false));   // a tap still clears one block
        double vy = -1;
        for (int i = 0; i < 80; i++) vy = (vertical(vy, false, false) - GRAVITY) * .98;
        assertTrue(vy >= MAX_FALL - 1e-6, "terminal " + vy);
    }

    static double apex(boolean hold) {
        double y = 0, vy = .42, top = 0;
        for (int t = 0; t < 40; t++) {
            boolean held = hold || t < 2, released = !hold && t == 2;
            vy = vertical(vy, held, released);
            y += vy; top = Math.max(top, y);
            vy = (vy - GRAVITY) * .98;
        }
        return top;
    }

    @Test void dashEasesOut() {
        assertEquals(1, dashScale(MovementRules.DASH_TICKS));
        assertTrue(dashScale(1) < dashScale(3));
        assertTrue(DASH_EXIT_SPEED > TOP_SPEED);
    }

    @Test void squashSpringOvershootsThenSettles() {
        var s = new Spring(1, SQUASH_STIFFNESS, SQUASH_DAMPING);
        s.kick(-SQUASH_LAND);
        float min = 1, max = 1;
        for (int i = 0; i < 60; i++) { s.step(1, 1 / 60f); min = Math.min(min, s.value); max = Math.max(max, s.value); }
        assertTrue(min < .9f, "no squash " + min);
        assertTrue(max > 1.001f, "no overshoot");
        for (int i = 0; i < 120; i++) s.step(1, 1 / 60f);
        assertEquals(1, s.value, .01);
    }

    @Test void springIsFrameRateIndependent() {
        var a = new Spring(0, POSE_STIFFNESS, POSE_DAMPING); var b = new Spring(0, POSE_STIFFNESS, POSE_DAMPING);
        for (int i = 0; i < 30; i++) a.step(.2f, 1 / 30f);
        for (int i = 0; i < 144; i++) b.step(.2f, 1 / 144f);
        assertEquals(a.value, b.value, .01);
    }

    @Test void facingTurnsTheShortWay() {
        assertTrue(approachAngle(170, -170, FACING_RATE, .05f) > 170);
        assertEquals(90, approachAngle(0, 90, 1000, 1), 1e-3);
    }
}
