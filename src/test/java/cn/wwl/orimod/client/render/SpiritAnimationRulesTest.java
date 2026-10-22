package cn.wwl.orimod.client.render;

import cn.wwl.orimod.asset.AnimationContract;
import com.google.gson.JsonParser;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.gltf.blend.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SpiritAnimationRulesTest {
    private static SpiritAnimationRules.State state() { return new SpiritAnimationRules.State(); }

    @Test void zeroDurationLaunchEventSurvivesInputModeResetAndIdleDoesNotReplayIt() {
        var movement = new cn.wwl.orimod.movement.MovementState();
        movement.observeAnimationImpulse(cn.wwl.orimod.movement.MoveMode.LAUNCH, 7);
        movement.mode = cn.wwl.orimod.movement.MoveMode.IDLE; // SpiritControls' next input tick
        assertEquals("launch", movement.animationImpulse.id()); assertEquals(7, movement.animationImpulseId);
        movement.observeAnimationImpulse(cn.wwl.orimod.movement.MoveMode.IDLE, 8);
        movement.observeAnimationImpulse(cn.wwl.orimod.movement.MoveMode.WALL_SLIDE, 9);
        assertEquals(7, movement.animationImpulseId);
        movement.observeAnimationImpulse(cn.wwl.orimod.movement.MoveMode.STOMP_LAND, 10);
        movement.observeAnimationImpulse(cn.wwl.orimod.movement.MoveMode.LAUNCH, 7);
        assertEquals("stomp_land", movement.animationImpulse.id()); assertEquals(10, movement.animationImpulseId);
    }

    @Test void heldFeetIgnoreResidualGravityAndClimbTracksActualDisplacement() {
        assertEquals(0, SpiritAnimationRules.contactVertical(0, .05f, true, -.0784f));
        assertEquals(.15f, SpiritAnimationRules.contactVertical(.15, .05f, true, -.0784f), 1e-6);
        assertEquals(-.15f, SpiritAnimationRules.contactVertical(-.15, .05f, true, -.0784f), 1e-6);
        assertEquals(-.0784f, SpiritAnimationRules.contactVertical(20, .05f, false, -.0784f));
        assertEquals(0, SpiritAnimationRules.contactVertical(0, 0, true, 0));
    }
    @Test void shortActionRetainsIdentityForVanillaSwingTailButNeverLingers() {
        assertTrue(SpiritAnimationRules.actionVisible(.2f, .333f, false));
        assertTrue(SpiritAnimationRules.actionVisible(.38f, .333f, true));
        assertFalse(SpiritAnimationRules.actionVisible(.38f, .333f, false));
        assertFalse(SpiritAnimationRules.actionVisible(.51f, .333f, true));
        assertFalse(SpiritAnimationRules.actionVisible(-1, .333f, true));
    }

    @Test void dryOneBlockCrawlNeverUsesWaterOrExternalPitch() {
        var s = state(); s.crawling = true; s.speed = 4;
        assertEquals("crawl", SpiritAnimationRules.base(s));
        assertEquals(0, SpiritAnimationRules.bodyPitch(SpiritAnimationRules.family(s), 4, 0, 0));
    }
    @ParameterizedTest @CsvSource({"0,false,swim_idle", "2,false,swim_surface", "2,true,swim", "19,true,swim_dash"})
    void waterStatesAreDistinct(float speed, boolean submerged, String clip) {
        var s = state(); s.water = true; s.grounded = false; s.submerged = submerged; s.speed = speed;
        assertEquals(clip, SpiritAnimationRules.base(s));
    }
    @ParameterizedTest @ValueSource(strings = {"crawl", "swim_idle", "swim_surface"})
    void authoredRootTiltIsNeverAppliedTwice(String family) {
        assertEquals(0, SpiritAnimationRules.bodyPitch(family, 2, -1, 25));
    }
    @Test void elytraAndGlideUseDifferentAssetsAndPitch() {
        var s = state(); s.elytra = true;
        assertEquals("elytra", SpiritAnimationRules.base(s));
        assertEquals(Math.PI / 2, SpiritAnimationRules.bodyPitch("elytra", 4, 0, 0), 1e-6);
        s.elytra = false; s.mode = "glide";
        assertEquals("glide", SpiritAnimationRules.base(s));
        assertEquals(.1, SpiritAnimationRules.bodyPitch("glide", 4, 0, 0), 1e-6);
    }
    @ParameterizedTest @CsvSource({".15,wall_climb", "0,wall_hold", "-.15,wall_slide"})
    void r55WallModesFollowSignedVelocity(float vertical, String clip) {
        var s = state(); s.mode = "wall_slide"; s.vertical = vertical;
        assertEquals(clip, SpiritAnimationRules.base(s));
    }
    @Test void ladderPausesAndRetracesItsPhaseOnDescent() {
        assertEquals(0, SpiritAnimationRules.climbRate(0));
        assertEquals(-SpiritAnimationRules.climbRate(.1f), SpiritAnimationRules.climbRate(-.1f));
        float p = .2f;
        for (int i = 0; i < 20; i++) p = SpiritAnimationRules.cycle(p + .05f * SpiritAnimationRules.climbRate(.1f));
        for (int i = 0; i < 20; i++) p = SpiritAnimationRules.cycle(p + .05f * SpiritAnimationRules.climbRate(-.1f));
        assertEquals(.2, p, 1e-5);
    }
    @Test void mantleLaunchStompAndBoatAreExplicit() {
        var s = state(); s.mantle = true; s.climbing = true;
        assertEquals("mantle", SpiritAnimationRules.base(s));
        s.mantle = s.climbing = false; s.launchCharge = true;
        assertEquals("launch_charge", SpiritAnimationRules.base(s));
        s.shot = "launch"; s.grounded = false;
        assertEquals("launch", SpiritAnimationRules.base(s));
        s.mode = "stomp"; s.shot = "stomp_start";
        assertEquals("stomp_start", SpiritAnimationRules.base(s));
        s.shot = ""; assertEquals("stomp", SpiritAnimationRules.base(s));
        s.mode = "stomp_land"; s.shot = "land_heavy";
        assertEquals("land_heavy", SpiritAnimationRules.base(s));
        s.passenger = true; assertEquals("sit", SpiritAnimationRules.base(s));
        s.rowing = true; assertEquals("boat_row", SpiritAnimationRules.base(s));
        s.alive = false; assertEquals("death", SpiritAnimationRules.base(s));
    }
    @Test void crouchWalkAndBurrowDoNotBorrowOrdinaryAirOrWalk() {
        var s = state(); s.crouching = true;
        assertEquals("crouch", SpiritAnimationRules.base(s));
        s.moving = true; s.launchCharge = true; assertEquals("crouch_walk", SpiritAnimationRules.base(s));
        s.burrow = true; assertEquals("burrow", SpiritAnimationRules.base(s));
    }
    @ParameterizedTest @ValueSource(strings = {"idle", "wall_slide", "glide", "double_jump", "wall_jump", "launch"})
    void acceptedCombatCanShowItsOverlayInMovementStates(String mode) {
        var s = state(); s.mode = mode; s.water = true; s.climbing = true; s.grounded = false;
        assertTrue(SpiritAnimationRules.hands(s));
        s.passenger = true; assertFalse(SpiritAnimationRules.hands(s));
    }
    @ParameterizedTest @ValueSource(strings = {"dash", "bash_aim", "grapple", "stomp"})
    void authoritativeActionOwnersStillExcludeCombat(String mode) {
        var s = state(); s.mode = mode; assertFalse(SpiritAnimationRules.hands(s));
    }
    @ParameterizedTest @CsvSource({"EAT,eat", "DRINK,drink", "TOOT_HORN,horn", "BRUSH,brush", "BLOCK,block", "SPEAR,throw_ready", "SPYGLASS,use_item"})
    void distinctItemUseAssets(String use, String clip) { assertEquals(clip, SpiritAnimationRules.use(use)); }
    @ParameterizedTest @CsvSource({"1,0,hurt", "-1,0,hurt_back", "0,1,hurt_side", "0,-1,hurt_side_left"})
    void hurtDirectionUsesBodyFrame(float front, float right, String clip) {
        assertEquals(clip, SpiritAnimationRules.hurt(front, right, 2, 20, .1f));
    }
    @Test void severeDamageOrImpulseUseHeavyReaction() {
        assertEquals("hurt_heavy", SpiritAnimationRules.hurt(1, 0, 5, 20, 0));
        assertEquals("hurt_heavy", SpiritAnimationRules.hurt(0, -1, 1, 20, .7f));
    }
    @Test void allSpeedsAndAirHeightsKeepNormalizedLayers() {
        for (float speed = 0; speed < 20; speed += .25f) for (float vy = -1; vy <= 1; vy += .1f)
            for (float fade : new float[]{0, .2f, .5f, 1}) {
                float[] w = SpiritAnimationRules.weights(speed, 1 - fade, vy, fade);
                assertEquals(1, w[0] + w[1] + w[2] + w[3], 1e-5);
                for (float v : w) assertTrue(v >= -1e-6 && v <= 1);
            }
    }
    @Test void realGemRenderEvaluatorGivesRunAndRiseFullOwnershipInsteadOfFiftyPercent() {
        // Translation makes normalization measurable. The old base=1 + run=1 produces 5 instead of 10.
        var rest = new float[NodeTable.TRS_STRIDE]; rest[NodeTable.ROTATION + 3] = 1;
        rest[NodeTable.SCALE] = rest[NodeTable.SCALE + 1] = rest[NodeTable.SCALE + 2] = 1;
        var table = NodeTable.ofNodes(new String[]{"spine"}, new int[]{-1}, rest);
        GltfAnimation low = translation("walk", 0), high = translation("run", 10);
        float[] gait = SpiritAnimationRules.weights(8, 1, 0, 0);
        assertEquals(0, gait[0], 1e-6); assertEquals(1, gait[1], 1e-6);
        var blend = new AnimationBlend(); blend.override(low, 0, gait[0]); blend.override(high, 0, gait[1]);
        var result = table.newScratch(); BlendEvaluator.evaluate(table, blend, result, new BlendEvaluator.Scratch());
        assertEquals(10, result[NodeTable.TRANSLATION], 1e-6, "Run must include the spine, not only legs");
        float[] rise = SpiritAnimationRules.weights(0, 0, 1, 1);
        blend.clear(); blend.override(low, 0, rise[0]); blend.override(high, 0, rise[2]);
        BlendEvaluator.evaluate(table, blend, result, new BlendEvaluator.Scratch());
        assertEquals(10, result[NodeTable.TRANSLATION], 1e-6);
    }
    private static GltfAnimation translation(String name, float x) {
        return GltfAnimation.procedural(name, new PoseDriver() {
            public void apply(float parameter, float[] state) { state[NodeTable.TRANSLATION] = x; }
            public float cycleSeconds() { return 1; }
        });
    }
    @Test void runtimeLoopAndMirrorContractMatchesPromotedAssetManifest() throws Exception {
        var path = Path.of(System.getProperty("orimod.projectDir"), "art/source/character/manifest.json");
        var clips = JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("animation").getAsJsonObject("clips");
        assertEquals(clips.keySet(), new java.util.HashSet<>(AnimationContract.REQUIRED));
        for (var entry : clips.entrySet()) {
            String name = entry.getKey();
            assertEquals(entry.getValue().getAsJsonObject().get("loop").getAsBoolean(), SpiritAnimationRules.loops(name), name);
            if (!name.endsWith("_left")) assertEquals(clips.has(name + "_left") ? name + "_left" : name, SpiritAnimationRules.handed(name, true));
        }
    }
}
