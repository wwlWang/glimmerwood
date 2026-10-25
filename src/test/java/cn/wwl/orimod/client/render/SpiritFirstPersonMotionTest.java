package cn.wwl.orimod.client.render;

import cn.wwl.orimod.client.render.SpiritFirstPersonMotion.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** R114: first-person weapon curves return to rest, never jump, and keep one fixed paw-to-item grip. */
class SpiritFirstPersonMotionTest {
    // Mirrors SpiritCombat's WINDUP {2,3,4}, hit 2 and RECOVERY {8,8,6}.
    private static final int[] WINDUP = {0, 2, 3, 4}, RECOVERY = {0, 8, 8, 6};

    private static Pose blade(int stage, float t, Pose from) {
        return SpiritFirstPersonMotion.blade(stage, t, WINDUP[stage], 2, RECOVERY[stage], from);
    }

    private static float distance(Pose a, Pose b) {
        float d = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
        return d + (Math.abs(a.pitch() - b.pitch()) + Math.abs(a.yaw() - b.yaw()) + Math.abs(a.roll() - b.roll())) / 180;
    }

    /** Sampled every 1/20 tick, no step may exceed {@code limit}: no snaps between keyframe segments. */
    private static void continuous(Function<Float, Pose> curve, float end, float limit) {
        var previous = curve.apply(0f);
        for (float t = .05f; t <= end; t += .05f) {
            var next = curve.apply(t);
            assertTrue(distance(previous, next) < limit, "jump at " + t + ": " + previous + " -> " + next);
            previous = next;
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void bladeStagesStartAndEndAtRestWithoutJumps(int stage) {
        int duration = WINDUP[stage] + 2 + RECOVERY[stage];
        assertEquals(0, distance(Pose.REST, blade(stage, 0, Pose.REST)), 1e-5);
        assertEquals(0, distance(Pose.REST, blade(stage, duration, Pose.REST)), 1e-5);
        continuous(t -> blade(stage, t, Pose.REST), duration, .5f);
    }

    @Test void bladeSweepsAlternateAcrossTheViewAndTheFinisherCutsDown() {
        var cock1 = blade(1, WINDUP[1], Pose.REST); var end1 = blade(1, WINDUP[1] + 2, Pose.REST);
        assertTrue(cock1.roll() < -30 && end1.x() < cock1.x() - .3f && end1.yaw() > cock1.yaw() + 45, "forehand toward the off side");
        var cock2 = blade(2, WINDUP[2], Pose.REST); var end2 = blade(2, WINDUP[2] + 2, Pose.REST);
        assertTrue(cock2.x() < 0 && end2.x() > 0 && cock2.yaw() > 0 && end2.yaw() < 0, "backhand returns");
        var cock3 = blade(3, WINDUP[3], Pose.REST); var end3 = blade(3, WINDUP[3] + 2, Pose.REST);
        assertTrue(cock3.y() > end3.y() + .4f && cock3.z() < 0 && end3.pitch() < -90, "overhead cut ends forward/down");
    }

    @Test void chainedStageWindsUpFromWhereThePreviousLeftTheBlade() {
        var held = blade(1, WINDUP[1] + 3, Pose.REST);
        assertEquals(0, distance(held, blade(2, 0, held)), 1e-5);
    }

    @Test void hammerRaisesStrikesAndRecovers() {
        assertEquals(0, distance(Pose.REST, SpiritFirstPersonMotion.hammerCharge(0, 0)), 1e-5);
        var raised = SpiritFirstPersonMotion.hammerCharge(1, 0);
        assertTrue(raised.y() > .2f && raised.roll() > 10, "hammer lifted upright overhead");
        float end = SpiritFirstPersonMotion.HAMMER_STRIKE_TICKS + SpiritFirstPersonMotion.HAMMER_RECOVER_TICKS;
        for (boolean plunge : new boolean[]{false, true}) {
            var struck = SpiritFirstPersonMotion.hammerStrike(SpiritFirstPersonMotion.HAMMER_STRIKE_TICKS, plunge);
            assertTrue(struck.pitch() < -90 && struck.y() < raised.y() - .3f, "head driven forward and down");
            assertEquals(0, distance(Pose.REST, SpiritFirstPersonMotion.hammerStrike(end, plunge)), 1e-4);
            continuous(t -> SpiritFirstPersonMotion.hammerStrike(t, plunge), end, .5f);
        }
        assertEquals(0, distance(raised, SpiritFirstPersonMotion.hammerStrike(0, false)), 1e-4);
        assertEquals(0, distance(raised, SpiritFirstPersonMotion.hammerPlunge(0)), 1e-4);
        assertEquals(0, distance(Pose.REST, SpiritFirstPersonMotion.hammerLower(5, .6f)), 1e-5);
    }

    @Test void spearFollowsTheImmediateThrowInsteadOfInventingACharge() {
        var carry=SpiritFirstPersonMotion.spearCarry();
        assertEquals(carry,SpiritFirstPersonMotion.spearThrow(0));
        var follow=SpiritFirstPersonMotion.spearThrow(1.5f);
        assertTrue(follow.x()<-.3 && follow.z()<-.2,"hand travels forward and across immediately");
        assertEquals(follow,SpiritFirstPersonMotion.spearThrow(3));
        assertEquals(carry,SpiritFirstPersonMotion.spearThrow(8));
        continuous(t->SpiritFirstPersonMotion.spearThrow(t),24,.5f);
    }

    @Test void spearLeavesWithinTwoTicksEvenIfTheCooldownPacketIsLate() {
        for(float cooldown:new float[]{0,.5f,1}) {
            assertEquals(1,SpiritFirstPersonMotion.spearPresence(0,cooldown),1e-6);
            assertEquals(.5f,SpiritFirstPersonMotion.spearPresence(1,cooldown),1e-6);
            assertEquals(0,SpiritFirstPersonMotion.spearPresence(2,cooldown),1e-6);
            assertEquals(0,SpiritFirstPersonMotion.spearPresence(3,cooldown),1e-6);
        }
        assertEquals(0,SpiritFirstPersonMotion.spearPresence(12,.5f),1e-6);
        assertEquals(.5f,SpiritFirstPersonMotion.spearPresence(20,.125f),1e-6);
        assertEquals(1,SpiritFirstPersonMotion.spearPresence(24,0),1e-6);
    }

    @Test void bowFollowsVanillasDrawCurveAndLowersAfterTheKick() {
        assertEquals(0, SpiritFirstPersonMotion.bowPull(0), 1e-6);
        assertEquals(1, SpiritFirstPersonMotion.bowPull(20), 1e-6);
        assertEquals((.25f + 1) / 3, SpiritFirstPersonMotion.bowPull(10), 1e-6);
        assertEquals(0, SpiritFirstPersonMotion.bowDraw(0).raise(), 1e-6);
        assertEquals(1, SpiritFirstPersonMotion.bowDraw(SpiritFirstPersonMotion.BOW_RAISE_TICKS).raise(), 1e-6);
        var kicked = SpiritFirstPersonMotion.bowRelease(1.5f);
        assertEquals(1, kicked.raise(), 1e-6); assertEquals(1, kicked.kick(), 1e-6);
        var lowered = SpiritFirstPersonMotion.bowRelease(SpiritFirstPersonMotion.BOW_RECOIL_TICKS);
        assertEquals(0, lowered.raise(), 1e-6); assertEquals(0, lowered.kick(), 1e-6);
        float previous = 1;
        for (float t = 1.5f; t <= SpiritFirstPersonMotion.BOW_RECOIL_TICKS; t += .05f) {
            float raise = SpiritFirstPersonMotion.bowRelease(t).raise();
            assertTrue(raise <= previous + 1e-6f && previous - raise < .05f, "smooth lowering at " + t);
            previous = raise;
        }
    }

    @Test void leftHandMirrorsLateralMotion() {
        var p = new Pose(.2f, .1f, -.3f, -40, 30, 15).side(true);
        assertEquals(new Pose(-.2f, .1f, -.3f, -40, -30, -15), p);
    }

    @Test void palmKeepsTheThirdPersonGripInAnyView() {
        var itemInPalm = new Quaternionf().rotationXYZ(.4f, -1.2f, .9f);
        var itemInView = new Quaternionf().rotationXYZ(-2.9f, .3f, 2.2f);
        var palm = SpiritItemGrip.palmFromItem(itemInView, itemInPalm);
        var recomposed = new Quaternionf(palm).mul(itemInPalm);
        assertTrue(Math.abs(recomposed.dot(itemInView)) > .99999f, "palm * item-in-palm must equal the item in view");
        // The fingers' side follows the item: the same item vector stays on the same paw axis in every view.
        var handle = new Vector3f(1, 1, 0).normalize();
        var inPalm = itemInPalm.transform(new Vector3f(handle));
        var inView = itemInView.transform(new Vector3f(handle));
        assertTrue(palm.transform(new Vector3f(inPalm)).distance(inView) < 1e-5f);
    }
}
