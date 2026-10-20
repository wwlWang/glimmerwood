package cn.wwl.orimod.client.fx;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class CompanionMotionTest {
    @ParameterizedTest
    @CsvSource({"4, none", "10, none", "4, hint", "10, hint", "4, seek", "10, seek"})
    void followsMovingAnchorForThreeSecondsIncludingExcursionReturn(double speed, String excursion) {
        var origin = new Vec3(2000, 201, -2000);
        var state = new CompanionMotion.State(origin);
        if (!excursion.equals("none")) {
            boolean hint = excursion.equals("hint");
            state.seek(origin.add(-10, 0, 0), hint);
            for (int i = 0; i < (hint ? CompanionMotion.HINT_TICKS : CompanionMotion.SEEK_TICKS); i++)
                state.step(origin, false);
            assertEquals(true, state.diagnostics().get("returning"));
        }
        for (int tick = 1; tick <= 60; tick++) {
            var anchor = origin.add(speed * tick * CompanionMotion.STEP_SECONDS, 0, 0);
            state.step(anchor, false);
            if (tick >= 40) assertTrue(state.pos.distanceTo(anchor) < 2.5,
                    "speed=" + speed + ", excursion=" + excursion + ", lag=" + state.pos.distanceTo(anchor));
        }
        assertTrue(Double.isFinite(state.vel.x));
    }

    @Test void springConvergesWithinASecondWithoutOvershooting() {
        var state = new CompanionMotion.State(new Vec3(2, 0, 0));
        double min = 2;
        for (int i = 0; i < 20; i++) { state.step(Vec3.ZERO, false); min = Math.min(min, state.pos.x); }
        assertTrue(state.pos.length() < .05, "one-second error " + state.pos.length());
        assertTrue(min >= -.1, "overshoot exceeds five percent");
    }

    @Test void distanceAboveThreeSnapsAndClearsVelocityAndInterpolation() {
        var state = new CompanionMotion.State(new Vec3(3.001, 0, 0));
        state.vel = new Vec3(10, 0, 0);
        state.step(Vec3.ZERO, false);
        assertEquals(Vec3.ZERO, state.pos);
        assertEquals(Vec3.ZERO, state.vel);
        assertEquals(Vec3.ZERO, state.position(.5f));
    }

    @Test void distanceExactlyThreeUsesSpring() {
        var state = new CompanionMotion.State(new Vec3(3, 0, 0));
        state.step(Vec3.ZERO, false);
        assertTrue(state.pos.x > 0 && state.pos.x < 3);
    }

    @Test void dartLastsFourTicksThenReturnsNaturally() {
        var state = new CompanionMotion.State(Vec3.ZERO);
        state.dart(new Vec3(8, 0, 0));
        for (int i = 0; i < 4; i++) { assertEquals(4 - i, state.dartTicks); state.step(Vec3.ZERO, false); }
        assertEquals(0, state.dartTicks);
        assertNull(state.dartTarget);
        assertTrue(state.pos.x > .5);
        for (int i = 0; i < 30; i++) state.step(Vec3.ZERO, false);
        assertTrue(state.pos.length() < .05);
    }

    @Test void hintCanFlyBeyondSnapRadiusAndReturnWithoutTeleporting() {
        var state = new CompanionMotion.State(Vec3.ZERO);
        state.seek(new Vec3(10, 0, 0), true);
        state.step(Vec3.ZERO, false);
        assertTrue(state.pos.x > 0 && state.pos.x < 3);
        for (int i = 1; i < CompanionMotion.HINT_TICKS; i++) state.step(Vec3.ZERO, false);
        assertTrue(state.pos.x > 9.9);
        assertFalse(state.guiding());
        assertNull(state.seekTarget);
        state.step(Vec3.ZERO, false);
        assertTrue(state.pos.x > 3, "return should spring rather than snap");
        for (int i = 0; i < 40; i++) state.step(Vec3.ZERO, false);
        assertTrue(state.pos.length() < .05);
    }

    @Test void ownerTeleportCancelsDartAndSeekEvenDuringAnExcursion() {
        var state = new CompanionMotion.State(Vec3.ZERO);
        state.seek(new Vec3(10, 0, 0), true);
        state.dart(new Vec3(5, 0, 0));
        var destination = new Vec3(100, 20, 30);
        state.step(destination, true);
        assertEquals(destination, state.position(.3f));
        assertEquals(Vec3.ZERO, state.vel);
        assertEquals(0, state.seekTicks);
        assertEquals(0, state.dartTicks);
    }

    @Test void hintTakesPriorityOverLightSeeking() {
        var state = new CompanionMotion.State(Vec3.ZERO);
        var hint = new Vec3(5, 0, 0);
        state.seek(hint, true);
        state.seek(new Vec3(-5, 0, 0), false);
        assertEquals(hint, state.seekTarget);
        assertEquals(CompanionMotion.HINT_TICKS, state.seekTicks);
    }

    @Test void itemExcursionExpiresAfterThirtyTicks() {
        var state = new CompanionMotion.State(Vec3.ZERO);
        state.seek(new Vec3(5, 0, 0), false);
        for (int i = 0; i < 30; i++) state.step(Vec3.ZERO, false);
        assertEquals(0, state.seekTicks);
        assertNull(state.seekTarget);
        for (int i = 0; i < 40; i++) state.step(Vec3.ZERO, false);
        assertTrue(state.pos.length() < .05);
    }

    @Test void interpolationDoesNotAdvanceSimulation() {
        var state = new CompanionMotion.State(new Vec3(2, 0, 0));
        state.step(Vec3.ZERO, false);
        var before = state.pos;
        assertEquals(state.prevPos.lerp(state.pos, .5), state.position(.5f));
        assertEquals(before, state.pos);
        assertEquals(state.prevPos, state.position(-1));
        assertEquals(state.pos, state.position(2));
    }

    @Test void anchorUsesBodyYawAndLocomotionOffsets() {
        var normal = anchor(0, 0, false, false, false);
        assertEquals(-.45, normal.x, 1e-6);
        assertEquals(1.8, normal.y, 1e-6);
        assertEquals(-.25, normal.z, 1e-6);
        var turned = anchor(90, 0, false, false, false);
        assertEquals(.25, turned.x, 1e-6); assertEquals(-.45, turned.z, 1e-6);
        assertEquals(-.6, anchor(0, 5, false, false, false).z, 1e-6);
        assertEquals(normal, anchor(0, 5, true, false, false));
        var glide = anchor(0, 0, false, true, false);
        assertEquals(2.3, glide.y, 1e-6); assertEquals(normal.x / 2, glide.x, 1e-6);
        assertEquals(normal.z / 2, glide.z, 1e-6);
        assertEquals(1.1, anchor(0, 0, false, false, true).y, 1e-6);
    }

    @Test void seekingRequiresIdlePeaceAndNoGuide() {
        assertTrue(CompanionMotion.eligibleForSeek(.49, 101, false));
        assertFalse(CompanionMotion.eligibleForSeek(.5, 101, false));
        assertFalse(CompanionMotion.eligibleForSeek(0, 100, false));
        assertFalse(CompanionMotion.eligibleForSeek(0, 101, true));
    }

    @Test void moodHasCalmCombatAndLowHealthPulse() {
        assertEquals(CompanionMotion.CALM, CompanionMotion.moodTarget(false, false, 0));
        assertEquals(CompanionMotion.COMBAT, CompanionMotion.moodTarget(true, false, 0));
        assertEquals(CompanionMotion.CALM, CompanionMotion.moodTarget(true, true, 0));
        assertEquals(CompanionMotion.WORRIED, CompanionMotion.moodTarget(false, true, .75));
        assertEquals(CompanionMotion.CALM, CompanionMotion.moodTarget(false, true, 1.5));
    }

    private static Vec3 anchor(float yaw, double speed, boolean dash, boolean glide, boolean swim) {
        return CompanionMotion.anchor(Vec3.ZERO, 2, yaw, 0, speed, dash, glide, swim);
    }
}
