package cn.wwl.orimod.movement;

import cn.wwl.orimod.combat.CombatView;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BladeMotionTest {
    @Test void threeSlashesSurviveFrictionAndNoInputBrakingWithoutCarryingIntoTheNextTick() {
        var motion = new BladeMotion();
        Vec3 velocity = Vec3.ZERO, previousStep = Vec3.ZERO;
        double total = 0;
        for (int stage = 1; stage <= 3; stage++) {
            double start = total;
            var view = new CombatView(stage, "blade", stage, stage * 20);
            for (int tick = 0; tick < 8; tick++) {
                motion.observe(view, true, true, 0);
                var base = FeelTuning.horizontal(velocity.scale(1 / .546).subtract(previousStep), Vec3.ZERO, true, 1, 1);
                previousStep = motion.ground(base, true, path -> true);
                Vec3 travel = base.add(previousStep);
                total += travel.z;
                velocity = travel.scale(.546);
            }
            assertEquals(stage == 3 ? .6 : .4, total - start, 1e-9);
        }
        assertEquals(1.4, total, 1e-9);
    }

    @Test void checksTheCombinedRemainingPathAndCancelsOnFailure() {
        var motion = new BladeMotion();
        motion.observe(new CombatView(1, "blade", 1, 0), true, true, 0);
        assertEquals(Vec3.ZERO, motion.ground(new Vec3(.2, 0, .3), true, path -> {
            assertEquals(.8, path.x, 1e-9); assertEquals(1.6, path.z, 1e-9); return false;
        }));
        assertEquals(0, motion.groundTicks());
        assertEquals(Vec3.ZERO, motion.ground(Vec3.ZERO, true, path -> true));
    }

    @Test void airHoldSurvivesVerticalShapingButOnlyThreeStartsUntilLanding() {
        var motion = new BladeMotion();
        for (int action = 1; action <= 4; action++) {
            assertEquals(action <= 3, motion.observe(new CombatView(action, "blade", 1, 0), true, false, 0));
            for (int tick = 0; tick < 3; tick++) {
                double shaped = FeelTuning.vertical(-.3, false, false);
                assertEquals(action <= 3 ? -.04 : shaped, motion.vertical(shaped), 1e-9);
            }
            assertEquals(-.3, motion.vertical(-.3), 1e-9);
            motion.observe(CombatView.IDLE, true, false, 0); // cancel must not replenish the per-jump budget
        }
        motion.observe(CombatView.IDLE, true, true, 0);
        assertTrue(motion.observe(new CombatView(5, "blade", 1, 0), true, false, 0));
        assertEquals(.3, motion.vertical(.3), 1e-9); // upward jump motion is preserved
    }

    @Test void disabledOrCancelledActionsCannotRestartTheirStep() {
        var motion = new BladeMotion(); var view = new CombatView(1, "blade", 1, 0);
        motion.observe(view, true, true, 0);
        motion.observe(view, false, true, 0);
        motion.observe(view, true, true, 0);
        assertEquals(0, motion.groundTicks());
    }

    private static final AABB BODY = new AABB(-.3, 1, -.3, .3, 2.2, .3);

    @Test void supportIncludesEndpointAndAllSoleSamples() {
        var floor = new AABB(-2, 0, -2, 2, 1, .8);
        assertTrue(BladeSupport.supported(BODY, new Vec3(0, 0, .5), p -> List.of(floor)));
        assertFalse(BladeSupport.supported(BODY, new Vec3(0, 0, .61), p -> List.of(floor)));
    }

    @Test void supportedEndpointsCannotBridgeAnEmptyMiddle() {
        var first = new AABB(-2, 0, -2, 2, 1, .6); var last = new AABB(-2, 0, 1.6, 2, 1, 4);
        assertFalse(BladeSupport.supported(BODY, new Vec3(0, 0, 2), p -> List.of(first, last)));
    }

    @Test void verticalTrapdoorAndLowerFloorAreNotLevelSupport() {
        assertFalse(BladeSupport.supported(BODY, Vec3.ZERO, p -> List.of(new AABB(-.5, 0, -.5, -.3125, 1, .5))));
        assertFalse(BladeSupport.supported(BODY, Vec3.ZERO, p -> List.of(new AABB(-2, 0, -2, 2, .5, 2))));
        assertTrue(BladeSupport.supported(BODY, Vec3.ZERO, p -> List.of(new AABB(-2, .8125, -2, 2, 1, 2))));
    }
}
