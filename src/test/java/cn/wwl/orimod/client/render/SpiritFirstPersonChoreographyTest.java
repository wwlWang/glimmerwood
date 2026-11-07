package cn.wwl.orimod.client.render;

import cn.wwl.orimod.client.render.SpiritFirstPersonMotion.Pose;
import cn.wwl.orimod.combat.SpiritCombat;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static cn.wwl.orimod.client.render.SpiritFirstPersonFrame.*;
import static org.junit.jupiter.api.Assertions.*;

/** First-person swing / draw / charge choreography: continuity, timing, handedness and on-screen framing. */
class SpiritFirstPersonChoreographyTest {
    private static final float TIP = SpiritWeaponRenderer.firstPersonTip(true, .98f);
    private static final float STEP = .05f;
    /** Largest pose change allowed between two samples .05 tick apart (one frame at 400 fps of game time). */
    private static final float MAX_JUMP = 9;

    private static Matrix4f bladeHand(boolean left, Pose pose) {
        float side = left ? -1 : 1;
        return SpiritFirstPersonMotion.arm(side, 0).mul(pose.matrix(side)).mul(leftNudge(left, false)).mul(display("spirit_blade", left));
    }
    private static Vector3f bladeTip(boolean left, Pose pose) { return ndc(bladeHand(left, pose), p(TIP, TIP)); }

    private static void assertClose(Pose a, Pose b, String what) { assertTrue(a.distance(b) < 1e-3f, what + ": " + a + " vs " + b); }

    // ---- blade -------------------------------------------------------------------------------------------------------
    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void bladeStageStartsFromTheShownPoseAndEndsAtRest(int stage) {
        var from = new Pose(5, -10, 3, .01f, -.02f, 0);
        assertClose(from, SpiritFirstPersonMotion.blade(stage, 0, from), "starts where the screen was");
        assertClose(Pose.REST, SpiritFirstPersonMotion.blade(stage, SpiritCombat.duration(stage), from), "returns to vanilla rest");
        assertClose(Pose.REST, SpiritFirstPersonMotion.blade(stage, SpiritCombat.duration(stage) + 5, from), "stays at rest");
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void cutLandsWithTheCrescentSweepAndHitWindow(int stage) {
        // BladeSlash sweeps for three ticks from windup-1; the hit window ends at recoveryStart.
        assertEquals(SpiritCombat.recoveryStart(stage), SpiritCombat.windup(stage) - 1 + 3);
        assertClose(SpiritFirstPersonMotion.BLADE_CUT[stage], SpiritFirstPersonMotion.blade(stage, SpiritCombat.recoveryStart(stage), Pose.REST), "cut at hit end");
        assertClose(SpiritFirstPersonMotion.BLADE_COCK[stage], SpiritFirstPersonMotion.blade(stage, SpiritCombat.windup(stage) - 1, Pose.REST), "cocked at sweep start");
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void bladeMotionHasNoJumpsIncludingAChainedStart(int stage) {
        // A chained stage starts from the previous stage's recovery, which is mid-motion at CHAIN_TICKS.
        // Stage 1 always starts from idle; stages 2 and 3 can chain.
        var chained = stage == 1 ? Pose.REST : SpiritFirstPersonMotion.blade(stage - 1, SpiritCombat.CHAIN_TICKS, Pose.REST);
        for (var from : new Pose[]{Pose.REST, chained}) {
            var previous = SpiritFirstPersonMotion.blade(stage, 0, from);
            for (float t = STEP; t <= SpiritCombat.duration(stage) + 1; t += STEP) {
                var pose = SpiritFirstPersonMotion.blade(stage, t, from);
                assertTrue(previous.distance(pose) < MAX_JUMP, "stage " + stage + " jumps at " + t + ": " + previous + " -> " + pose);
                previous = pose;
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void slashDirectionsMatchTheWorldCrescentForEachHand(boolean left) {
        float side = left ? -1 : 1;
        // Stage 1 cuts high to low toward the weapon side; stage 2 rises back across; the finisher drops from overhead.
        var cock1 = bladeTip(left, SpiritFirstPersonMotion.BLADE_COCK[1]); var cut1 = bladeTip(left, SpiritFirstPersonMotion.BLADE_CUT[1]);
        assertTrue(side * (cut1.x - cock1.x) > .3f && cut1.y < cock1.y - .3f, "stage 1 " + cock1 + " -> " + cut1);
        var cock2 = bladeTip(left, SpiritFirstPersonMotion.BLADE_COCK[2]); var cut2 = bladeTip(left, SpiritFirstPersonMotion.BLADE_CUT[2]);
        assertTrue(side * (cut2.x - cock2.x) < -.3f && cut2.y > cock2.y + .05f, "stage 2 " + cock2 + " -> " + cut2);
        var cock3 = bladeTip(left, SpiritFirstPersonMotion.BLADE_COCK[3]); var cut3 = bladeTip(left, SpiritFirstPersonMotion.BLADE_CUT[3]);
        assertTrue(cut3.y < cock3.y - .6f, "stage 3 drops " + cock3 + " -> " + cut3);
        assertTrue(cock3.y > bladeTip(left, Pose.REST).y + .2f, "finisher lifts above the idle tip");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bladeStaysFramedThroughEveryStage(boolean left) {
        for (int stage = 1; stage <= 3; stage++) for (float t = 0; t <= SpiritCombat.duration(stage); t += .25f) {
            var pose = SpiritFirstPersonMotion.blade(stage, t, Pose.REST);
            var hand = bladeHand(left, pose);
            var tip = ndc(hand, p(TIP, TIP)); var grip = ndc(hand, p(.18f, .18f));
            assertTrue(Math.abs(tip.x) < 1.25f && tip.y > -1 && tip.y < 1, "stage " + stage + " t" + t + " tip " + tip);
            assertTrue(grip.y > -1.25f && Math.abs(grip.x) < 1, "grip stays at the lower edge: " + grip);
            assertTrue(hand.transformPosition(p(TIP, TIP)).z < -.1f, "tip never reaches the near plane");
        }
    }

    @Test void comboClickCocksAtOnceAndFlowsIntoStageOneWithoutJumps() {
        // Click is local; the server stage arrives after latency. Simulate 0, 1.3 and 3 ticks of delay.
        for (float latency : new float[]{0, 1.3f, 3}) {
            var memory = new SpiritFirstPersonMotion.Memory();
            Pose previous = Pose.REST; float now = 0;
            for (; now < latency; now += STEP) previous = check(previous, memory.show(SpiritFirstPersonMotion.bladePending(now, memory.begin("pending", 0, now))));
            float start = now;
            for (; now < start + SpiritCombat.duration(1) + 1; now += STEP)
                previous = check(previous, memory.show(SpiritFirstPersonMotion.blade(1, now - start, memory.begin("blade", 1, start))));
            assertClose(Pose.REST, previous, "latency " + latency + " ends at rest");
        }
        assertClose(Pose.REST, SpiritFirstPersonMotion.bladePending(0, Pose.REST), "no pop on the click frame");
        assertClose(SpiritFirstPersonMotion.BLADE_COCK[1], SpiritFirstPersonMotion.bladePending(SpiritFirstPersonMotion.PENDING_TICKS, Pose.REST), "fully cocked");
    }

    @Test void refusedComboClickSettlesBack() {
        var memory = new SpiritFirstPersonMotion.Memory();
        Pose previous = Pose.REST; float now = 0;
        for (; now < 12; now += STEP) previous = check(previous, memory.show(SpiritFirstPersonMotion.bladePending(now, memory.begin("pending", 0, now))));
        for (; now < 12 + SpiritFirstPersonMotion.SETTLE_TICKS + 1; now += STEP) previous = check(previous, memory.show(memory.settle(now)));
        assertClose(Pose.REST, previous, "settled");
    }

    /** Visual pass, Spirit Glow on, 1274x720 viewport: the visible beam runs (1146,570)->(1206,420) and continues under
     * the tutorial toast. Its extension and this fixture both put the idle tip ~30 px inside the right edge. */
    @Test void idleRightTipMatchesTheVisualPassAndIsNotClippedAt16By9() {
        var tip = ndc(bladeHand(false, Pose.REST), p(TIP, TIP));
        float px = 3 + (tip.x + 1) / 2 * 1274, py = 40 + (1 - tip.y) / 2 * 720;
        float extended = 1206 + (1206 - 1146) / (570f - 420) * (420 - py);
        assertEquals(extended, px, 12, "fixture vs screenshot line");
        assertTrue(px < 1277 - 15, "tip inside the frame: " + px);
        var grip = ndc(bladeHand(false, Pose.REST), p(.40f, .40f));
        float gx = 3 + (grip.x + 1) / 2 * 1274, gy = 40 + (1 - grip.y) / 2 * 720;
        assertEquals(1206 + 60 / 150f * (420 - gy), gx, 25, "blade base lies on the same screenshot line");
    }

    // ---- hammer ------------------------------------------------------------------------------------------------------
    @Test void hammerWindsUpHoldsAndStrikesWithoutJumps() {
        var memory = new SpiritFirstPersonMotion.Memory();
        Pose previous = Pose.REST; float now = 0;
        // 16 ticks of wind-up, 2 ticks waiting for the server swing, then a six-tick strike, then settle.
        for (; now < 16; now += STEP) previous = check(previous, memory.show(SpiritFirstPersonMotion.hammerWindUp(now, memory.begin("wind", 0, now))));
        assertTrue(memory.shown.distance(SpiritFirstPersonMotion.HAMMER_WOUND) < 3, "fully cocked: " + memory.shown);
        for (; now < 18; now += STEP) previous = check(previous, memory.show(memory.shown));
        for (float s = 0; s <= 1; s += STEP / 6) previous = check(previous, memory.show(SpiritFirstPersonMotion.hammerStrike(s, memory.begin("strike", 0, now))));
        assertClose(Pose.REST, previous, "strike ends at rest");
        assertClose(SpiritFirstPersonMotion.HAMMER_IMPACT, SpiritFirstPersonMotion.hammerStrike(.4f, Pose.REST), "impact hold");
    }

    @Test void releasedWithoutWindUpStillSettles() {
        var memory = new SpiritFirstPersonMotion.Memory();
        memory.show(SpiritFirstPersonMotion.hammerWindUp(3, memory.begin("wind", 0, 0)));
        var shown = memory.shown; Pose previous = shown;
        for (float now = 3; now < 3 + SpiritFirstPersonMotion.SETTLE_TICKS + 1; now += STEP) previous = check(previous, memory.show(memory.settle(now)));
        assertClose(Pose.REST, previous, "settled");
        assertEquals("rest", memory.action);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void hammerHeadStaysOnScreenIncludingTheAirPlunge(boolean left) {
        float side = left ? -1 : 1;
        for (var pose : new Pose[]{Pose.REST, SpiritFirstPersonMotion.HAMMER_WOUND, SpiritFirstPersonMotion.HAMMER_IMPACT})
            for (float plunge : new float[]{0, 1}) {
                var hand = SpiritFirstPersonMotion.arm(side, 0).mul(pose.matrix(side)).mul(SpiritFirstPersonMotion.plunge(plunge))
                        .mul(leftNudge(left, false)).mul(display("spirit_hammer", left));
                var head = ndc(hand, p(.74f, .74f));
                assertTrue(Math.abs(head.x) < 1 && Math.abs(head.y) < 1, pose + " plunge " + plunge + " head " + head);
                assertTrue(side * head.x > 0, "head stays on its hand's side: " + head);
            }
    }

    // ---- spear -------------------------------------------------------------------------------------------------------
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void spearThrustDrivesTheTipForwardAndReturns(boolean left) {
        float side = left ? -1 : 1;
        Pose previous = SpiritFirstPersonMotion.spearThrust(0, Pose.REST);
        assertClose(Pose.REST, previous, "starts at rest");
        for (float s = STEP / 6; s <= 1; s += STEP / 6) previous = check(previous, SpiritFirstPersonMotion.spearThrust(s, Pose.REST));
        assertClose(Pose.REST, SpiritFirstPersonMotion.spearThrust(1, Pose.REST), "returns");
        Matrix4f rest = SpiritFirstPersonMotion.arm(side, 0).mul(leftNudge(left, false)).mul(display("spirit_spear", left));
        Matrix4f out = SpiritFirstPersonMotion.arm(side, 0).mul(SpiritFirstPersonMotion.SPEAR_THRUST.matrix(side)).mul(leftNudge(left, false)).mul(display("spirit_spear", left));
        float tip = SpiritWeaponRenderer.firstPersonTip(true, 1);
        assertTrue(out.transformPosition(p(tip, tip)).z < rest.transformPosition(p(tip, tip)).z - .15f, "thrust goes into the screen");
        var screen = ndc(out, p(tip, tip));
        assertTrue(Math.abs(screen.x) < 1 && Math.abs(screen.y) < 1, "thrust tip visible " + screen);
    }

    // ---- bow / spear use: vanilla matrices, eased ---------------------------------------------------------------------
    private static Matrix4f nativeBow(float k, float used, float pull) {
        var m = new Matrix4f().translate(k * -.2785682f, .18344387f, .15731531f)
                .rotateX(rad(-13.935f)).rotateY(rad(k * 35.3f)).rotateZ(rad(k * -9.785f));
        if (pull > .1f) m.translate(0, (float) Math.sin((used - .1f) * 1.3f) * (pull - .1f) * .004f, 0);
        return m.translate(0, 0, pull * .04f).scale(1, 1, 1 + pull * .2f).rotateY(rad(-k * 45));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void fullyRaisedBowIsExactlyVanillaAndEasesFromRest(boolean left) {
        float k = left ? -1 : 1;
        for (float used = 0; used <= 25; used += .5f) {
            float pull = SpiritFirstPersonMotion.bowPull(used);
            assertTrue(SpiritFirstPersonMotion.bowUse(k, used, pull, 1).equals(nativeBow(k, used, pull), 1e-5f), "native at " + used);
        }
        assertTrue(SpiritFirstPersonMotion.bowUse(k, 0, 0, 0).equals(new Matrix4f(), 1e-6f));
        assertTrue(SpiritFirstPersonMotion.spearUse(k, 0, 0, 0).equals(new Matrix4f(), 1e-6f));
        // Raise eases in over a few ticks; vanilla snapped on the first frame.
        assertEquals(0, SpiritFirstPersonMotion.raiseWeight(0), 1e-6);
        assertEquals(1, SpiritFirstPersonMotion.raiseWeight(SpiritFirstPersonMotion.RAISE_TICKS), 1e-6);
        float last = 0;
        for (float t = STEP; t < 4; t += STEP) {
            float w = SpiritFirstPersonMotion.raiseWeight(t), l = 1 - SpiritFirstPersonMotion.lowerWeight(t);
            assertTrue(w >= last && w - last < .1f, "raise is monotone and smooth"); last = w;
            assertTrue(l >= 0 && l <= 1);
        }
        assertEquals(1, SpiritFirstPersonMotion.lowerWeight(0), 1e-6);
        assertEquals(0, SpiritFirstPersonMotion.lowerWeight(SpiritFirstPersonMotion.LOWER_TICKS), 1e-6);
    }

    @Test void bowArmCurveIsTheBowPowerCurve() {
        for (int ticks = 0; ticks <= 30; ticks++)
            assertEquals(net.minecraft.world.item.BowItem.getPowerForTime(ticks), SpiritFirstPersonMotion.bowPull(ticks), 1e-6);
    }

    @Test void spearPullKeepsNativeEndpointsButLeadsEarly() {
        assertEquals(0, SpiritFirstPersonMotion.spearPull(0), 1e-6);
        assertEquals(1, SpiritFirstPersonMotion.spearPull(10), 1e-6);
        assertEquals(1, SpiritFirstPersonMotion.spearPull(40), 1e-6);
        for (float t = .5f; t < 10; t += .5f) assertTrue(SpiritFirstPersonMotion.spearPull(t) >= t / 10, "eases out, not behind native");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void raisedBowStaysOnItsHandSide(boolean left) {
        float k = left ? -1 : 1;
        for (float used = 0; used <= 20; used += 1) {
            float w = SpiritFirstPersonMotion.raiseWeight(used), pull = SpiritFirstPersonMotion.bowPull(used);
            var hand = SpiritFirstPersonMotion.arm(k, 0).mul(SpiritFirstPersonMotion.bowUse(k, used, pull, w)).mul(display("spirit_bow", left));
            var grip = ndc(hand, p(SpiritWeaponShape.BOW_X, SpiritWeaponShape.BOW_Y));
            assertTrue(k * grip.x > 0 && grip.y > -1 && grip.y < 1, "bow grip at " + used + ": " + grip);
        }
    }

    // ---- shared ------------------------------------------------------------------------------------------------------
    @Test void vanillaSwingPathMatchesTheArmAtBothEnds() {
        for (float side : new float[]{-1, 1}) for (float equip : new float[]{0, .4f}) {
            assertTrue(SpiritFirstPersonMotion.vanillaSwing(side, equip, 0).equals(SpiritFirstPersonMotion.arm(side, equip), 1e-6f));
            assertTrue(SpiritFirstPersonMotion.vanillaSwing(side, equip, 1).equals(SpiritFirstPersonMotion.arm(side, equip), 1e-5f));
        }
    }

    @Test void leftHandIsAnExactMirror() {
        var mirror = new Matrix4f().scaling(-1, 1, 1);
        for (var pose : new Pose[]{SpiritFirstPersonMotion.BLADE_CUT[1], SpiritFirstPersonMotion.BLADE_COCK[3],
                SpiritFirstPersonMotion.HAMMER_WOUND, SpiritFirstPersonMotion.SPEAR_THRUST})
            assertTrue(new Matrix4f(mirror).mul(pose.matrix(1)).mul(mirror).equals(pose.matrix(-1), 1e-5f), pose.toString());
        for (float used : new float[]{0, 3, 12})
            assertTrue(new Matrix4f(mirror).mul(SpiritFirstPersonMotion.bowUse(1, used, .5f, .7f)).mul(mirror)
                    .equals(SpiritFirstPersonMotion.bowUse(-1, used, .5f, .7f), 1e-5f));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sprintTuckKeepsTheBladeTipInsideTheViewAtFullBob(boolean left) {
        float side = left ? -1 : 1;
        assertClose(Pose.REST, SpiritFirstPersonMotion.sprintTuck(0), "no tuck standing still");
        var tuck = SpiritFirstPersonMotion.sprintTuck(SpiritFirstPersonMotion.MAX_BOB);
        assertTrue(tuck.dx() < 0, "tuck is inward for both hands");
        assertEquals(tuck, SpiritFirstPersonMotion.sprintTuck(1), "bounded beyond vanilla's max bob");
        for (float phase = 0; phase < 2; phase += .05f) {
            var bobbed = bob(SpiritFirstPersonMotion.MAX_BOB, phase);
            var untucked = ndc(new Matrix4f(bobbed).mul(bladeHand(left, Pose.REST)), p(TIP, TIP));
            var tucked = ndc(new Matrix4f(bobbed).translate(side * tuck.dx(), tuck.dy(), tuck.dz()).mul(bladeHand(left, Pose.REST)), p(TIP, TIP));
            assertTrue(Math.abs(tucked.x) < Math.abs(untucked.x), "tuck moves the tip inward");
            assertTrue(Math.abs(tucked.x) < .97f && Math.abs(tucked.y) < .97f, "tip inside at phase " + phase + ": " + tucked);
        }
    }

    @Test void bloomTrimIsFirstPersonOnlyAndBounded() {
        assertEquals(1, SpiritFirstPersonMotion.fpBloomScale(false, true));
        assertEquals(1, SpiritFirstPersonMotion.fpBloomScale(false, false));
        assertEquals(1, SpiritFirstPersonMotion.fpBloomScale(true, false));
        float trimmed = SpiritFirstPersonMotion.fpBloomScale(true, true);
        assertTrue(trimmed >= .6f && trimmed < .9f, "shaders on: trimmed but still readable " + trimmed);
    }

    private static Pose check(Pose previous, Pose next) {
        assertTrue(previous.distance(next) < MAX_JUMP, "jump " + previous + " -> " + next);
        return next;
    }
}
