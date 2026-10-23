package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraFollowTest {
    @Test void stopNeverPullsCameraBackwardsOrPastPlayerAtAnyFrameRate() {
        for (int fps : new int[]{4, 20, 60, 144}) for (double speed : new double[]{4, 6.4, 10}) {
            var state = CameraFollow.at(Vec3.ZERO);
            for (int frame = 1; frame <= fps * 3; frame++) {
                var target = new Vec3(speed * frame / fps, 0, 0);
                state = state.step(target, 1.0 / fps);
                assertTrue(state.position().distanceTo(target) <= FeelTuning.CAMERA_LAG_MAX + 1e-9);
            }
            var stopped = new Vec3(speed * 3, 0, 0);
            for (int frame = 0; frame < fps * 2; frame++) {
                double previous = state.position().x;
                state = state.step(stopped, 1.0 / fps);
                assertTrue(state.position().x >= previous - 1e-9, "Backward pull at " + fps + " FPS / " + speed);
                assertTrue(state.position().x <= stopped.x + 1e-9, "Overshoot at " + fps + " FPS / " + speed);
            }
            assertEquals(stopped.x, state.position().x, 1e-7);
        }
    }

    @Test void aFixedTargetHasFrameRateIndependentCriticalDamping() {
        var target = new Vec3(.3, .2, -.2);
        var a = CameraFollow.at(Vec3.ZERO); var b = a;
        for (int i = 0; i < 4; i++) a = a.step(target, 1.0 / 20);
        for (int i = 0; i < 12; i++) b = b.step(target, 1.0 / 60);
        assertEquals(0, a.position().distanceTo(b.position()), 1e-12);
        assertEquals(0, a.velocity().distanceTo(b.velocity()), 1e-12);
    }

    @Test void reversalDoesNotCarryOldMomentumAwayFromTarget() {
        var state = new CameraFollow(Vec3.ZERO, new Vec3(10, -5, 8));
        var target = new Vec3(-.2, .2, -.2);
        var next = state.step(target, .02);
        assertTrue(next.position().x < 0 && next.position().y > 0 && next.position().z < 0);
    }

    @Test void aSuddenStopWithExtremeVelocityCannotCrossTarget() {
        var target = new Vec3(.1, -.1, .1);
        var state = new CameraFollow(Vec3.ZERO, new Vec3(100, -100, 100)).step(target, .05);
        assertEquals(target, state.position());
        assertEquals(Vec3.ZERO, state.velocity());
    }

    @Test void zeroElapsedTimePreservesFollowWhilePaused() {
        var state = new CameraFollow(new Vec3(.1, .2, .3), new Vec3(4, 2, 1));
        assertSame(state, state.step(Vec3.ZERO, 0));
    }
}
