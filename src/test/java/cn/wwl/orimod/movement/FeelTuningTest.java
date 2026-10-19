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
        assertEquals(GRAVITY * fallGravityScale(-.3), -.3 - (vertical(-.3, true, false) - GRAVITY), 1e-9);
        assertEquals(GRAVITY * FALL_GRAVITY_SCALE, -.9 - (vertical(-.9, true, false) - GRAVITY), 1e-9);
        assertEquals(GRAVITY, .3 - (vertical(.3, false, false) - GRAVITY), 1e-9);
    }

    @Test void heldJumpGoesHigherThanTappedAndFallsAreCapped() {
        assertTrue(apex(true) > 1.35, "held " + apex(true));
        assertTrue(apex(false) < apex(true) * .7 && apex(false) >= .85, "tap " + apex(false));
        double vy = -1;
        for (int i = 0; i < 80; i++) vy = (vertical(vy, false, false) - GRAVITY) * .98;
        assertTrue(vy >= MAX_FALL - 1e-6, "terminal " + vy);
    }

    /** R88: a fall from the apex visibly accelerates: every tick adds more speed than the one before, up to the cap. */
    @Test void fallAccelerates() {
        double vy = -.001, prevStep = 0;
        for (int t = 0; t < 6; t++) {
            double next = (vertical(vy, false, false) - GRAVITY) * .98, step = vy - next;
            assertTrue(step > GRAVITY * 1.15, "tick " + t + " gains only " + step);
            assertTrue(step >= prevStep - 1e-9, "tick " + t + " gravity shrank");
            prevStep = step; vy = next;
        }
        assertTrue(vy < -.55, "6 ticks into the fall only " + vy);
        assertTrue(apex(true) > 1.5 && apex(true) < 1.8, "held " + apex(true));
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

    @Test void fovKickRampsWithSpeedAndPeaksOnDash() {
        assertEquals(0, fovKickTarget(false, 4.0), 1e-6);
        assertEquals(FOV_KICK_SPEED, fovKickTarget(false, 7.5), 1e-6);
        assertEquals(FOV_KICK_DASH, fovKickTarget(true, 0), 1e-6);
        assertTrue(fovKickTarget(false, 5.5) > 0 && fovKickTarget(false, 5.5) < FOV_KICK_SPEED);
    }

    @Test void trailIsContinuousAboveThresholdAndRespectsSettings() {
        assertEquals(0, trailMotes(.5, 1, true, 0));
        assertEquals(2, trailMotes(2, 1, true, 0));
        assertEquals(5, trailMotes(9, 1, true, 0));
        assertEquals(1, trailMotes(9, .5, true, 0) > 0 ? 1 : 0);
        assertEquals(1, trailMotes(9, 1, false, 0)); assertEquals(0, trailMotes(9, 1, false, 1));
    }

    @Test void groundOverspeedBleedsProportionallySoDashCarryIsShort() {
        var v = new net.minecraft.world.phys.Vec3(1.1, 0, 0); int ticks = 0;
        while (v.length() > TOP_SPEED + .01 && ticks < 40) { v = horizontal(v, new net.minecraft.world.phys.Vec3(1, 0, 0), true, 1, 1); ticks++; }
        assertTrue(ticks <= 10, "dash-speed carry lasted " + ticks + " ticks");
        assertTrue(ticks >= 4, "carry vanished too fast: " + ticks);
    }

    @Test void gaitWeightsBlendSmoothlyAndSumToOne() {
        float prevRun = -1;
        for (float v = 0; v <= 8; v += .1f) {
            float[] w = gaitWeights(v);
            assertEquals(1, w[0] + w[1] + w[2], 1e-5);
            float run = w[1] + w[2];
            assertTrue(run >= prevRun - 1e-6, "run weight must not drop with speed"); prevRun = run;
        }
        assertEquals(1, gaitWeights(.5f)[0], 1e-6);
        float[] mid = gaitWeights(2.4f); assertTrue(mid[0] > .2f && mid[1] > .2f, "true blend at mid speed");
        assertTrue(gaitWeights(9f)[2] > .99f); assertTrue(gaitWeights(6.4f)[1] > .99f, "top run speed is the run gait");
        for (float v = 0; v < 8; v += .05f) assertTrue(Math.abs(stride(v + .05f) - stride(v)) < .08f, "stride jumps at " + v);
    }

    @Test void cameraFollowIsFrameRateIndependentAndLeadIsClamped() {
        double a = 0, b = 0;
        for (int i = 0; i < 30; i++) a += (1 - a) * follow(CAMERA_LAG_RATE, 1 / 30.0);
        for (int i = 0; i < 144; i++) b += (1 - b) * follow(CAMERA_LAG_RATE, 1 / 144.0);
        assertEquals(a, b, 1e-6);
        assertEquals(CAMERA_LEAD_MAX, leadTarget(new net.minecraft.world.phys.Vec3(40, 5, 0)).length(), 1e-6);
        assertEquals(0, leadTarget(new net.minecraft.world.phys.Vec3(0, 9, 0)).length(), 1e-9);
        assertEquals(4 * CAMERA_LEAD_SECONDS, leadTarget(new net.minecraft.world.phys.Vec3(4, 0, 0)).length(), 1e-6);
    }

    @Test void trailIsWhiteAtTheHeadAndCoolBlueAtTheTail() {
        int head = trailColor(0, 3), tail = trailColor(2, 3);
        assertEquals(TRAIL_CORE, head); assertEquals(TRAIL_TAIL, tail);
        for (int c : new int[] {head, trailColor(1, 3), tail}) assertTrue((c & 255) >= (c >> 16 & 255), "never warm: " + Integer.toHexString(c));
    }

    /** R88: airborne pose weights are continuous, sum to 1, and go rise -> apex -> fall as the jump turns over. */
    @Test void airWeightsBlendSmoothly() {
        float prevRise = 2, prevFall = -1;
        for (float vy = .5f; vy >= -.6f; vy -= .01f) {
            float[] w = airWeights(vy);
            assertEquals(1, w[0] + w[1] + w[2], 1e-5);
            assertTrue(w[0] <= prevRise + 1e-6 && w[2] >= prevFall - 1e-6, "not monotonic at " + vy);
            prevRise = w[0]; prevFall = w[2];
        }
        assertEquals(1, airWeights(.4f)[0], 1e-6); assertEquals(1, airWeights(0)[1], 1e-6); assertEquals(1, airWeights(-.5f)[2], 1e-6);
    }

    @Test void airLeanNosesUpRisingAndDivesFalling() {
        assertTrue(airLean(0, .4f) < 0);
        assertTrue(airLean(0, -.6f) > 0);
        assertTrue(airLean(6.4f, 0) > airLean(0, 0));
        assertTrue(Math.abs(airLean(20, -3)) <= LEAN_MAX + 1e-6);
    }

    /** R88: holding the jump that started a jump never glides; a fresh press in the air does. */
    @Test void glideNeedsAFreshAirbornePress() {
        assertFalse(MovementRules.glideArmed(100, 100));      // press and jump on the same tick
        assertFalse(MovementRules.glideArmed(100, 104));      // buffered jump performed later
        assertTrue(MovementRules.glideArmed(110, 100));       // re-pressed after the jump
    }

    /** R88: sneaking only diverts a jump into Launch when Launch would fire. */
    @Test void sneakJumpIsAJumpUnlessLaunchIsReady() {
        assertFalse(MovementRules.crouchLaunches(true, false, true, 99, false));               // Launch not learned
        assertFalse(MovementRules.crouchLaunches(true, true, true, 3, false));                 // not charged
        assertTrue(MovementRules.crouchLaunches(true, true, true, MovementRules.LAUNCH_CHARGE_TICKS, false));
        assertFalse(MovementRules.crouchLaunches(false, true, true, 99, false));
    }
}
