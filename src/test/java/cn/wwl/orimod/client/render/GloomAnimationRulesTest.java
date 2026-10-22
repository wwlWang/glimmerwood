package cn.wwl.orimod.client.render;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import static cn.wwl.orimod.client.render.GloomAnimationRules.*;
import static org.junit.jupiter.api.Assertions.*;

class GloomAnimationRulesTest {
    private static State state(Kind kind, int action, int left) { return new State(kind, action, left, 1, 0, 0, 0, 0); }
    private static Selection choose(State state) { return select(state, 0, 100, 100); }

    @ParameterizedTest @EnumSource(Kind.class)
    void deathBeatsEveryOtherActionAndReachesFinalFrame(Kind kind) {
        var first = select(new State(kind, 1, 10, 2, 40, 1, 10, .4), 0, 0, 0);
        assertEquals("death", first.clip()); assertEquals(.05f, first.time(), 1e-6);
        var last = select(new State(kind, 1, 10, 2, 40, 20, 10, .4), 0, 0, 0);
        assertEquals(1, sample(last, 100, 1));
        assertEquals(1, sample(select(new State(kind, 0, 0, 1, 0, 25, 0, 0), .9f, 100, 100), 0, 1));
    }

    @ParameterizedTest @CsvSource({"SPITTER,0,14,telegraph", "BEETLE,1,15,telegraph", "HAWK,1,20,telegraph",
            "HEART,1,20,telegraph", "HEART,2,30,ring", "HEART,3,25,slam",
            "MATRIARCH,1,30,telegraph", "MATRIARCH,4,20,feathers", "MATRIARCH,5,25,gust"})
    void warningSamplesServerCountdownAndClamps(Kind kind, int action, int total, String clip) {
        assertEquals(clip, choose(state(kind, action, total)).clip());
        assertEquals(0, choose(state(kind, action, total)).time());
        var half = select(state(kind, action, (total + 1) / 2), total % 2 == 0 ? 0 : .5f, 0, 0);
        assertEquals(.5, half.time(), 1e-6); assertEquals(Timing.NORMALIZED, half.timing());
        assertEquals(1, select(state(kind, action, 1), 1, 0, 0).time());
        assertEquals(0, choose(state(kind, action, total + 100)).time());
    }

    @ParameterizedTest @CsvSource({"BEETLE,0,idle", "BEETLE,2,charge", "BEETLE,3,stunned", "BEETLE,4,flipped",
            "HAWK,0,idle", "HAWK,2,dive", "HAWK,3,move", "MATRIARCH,2,dive", "MATRIARCH,3,stunned"})
    void specialLoopStates(Kind kind, int action, String clip) {
        var value = choose(state(kind, action, 0)); assertEquals(clip, value.clip()); assertEquals(Timing.LOOP, value.timing());
    }

    @ParameterizedTest @EnumSource(value=Kind.class, names={"SPITTER","BEETLE","MATRIARCH"})
    void locomotionRespondsToSpeedWithoutChangingIdle(Kind kind) {
        assertEquals("idle", choose(new State(kind, 0, 0, 1, 0, 0, 0, MOVE_THRESHOLD)).clip());
        var slow = choose(new State(kind, 0, 0, 1, 0, 0, 0, .05));
        var fast = choose(new State(kind, 0, 0, 1, 0, 0, 0, .4));
        assertEquals("move", slow.clip()); assertTrue(fast.speed() > slow.speed());
    }

    @Test void releaseOnlyOnObservedEdgeAndNeverOnSpawnOrEnrage() {
        assertFalse(released(null, state(Kind.SPITTER, 0, 0)));
        assertFalse(released(state(Kind.SPITTER, 0, 0), state(Kind.SPITTER, 0, 0)));
        assertTrue(released(state(Kind.SPITTER, 0, 1), state(Kind.SPITTER, 0, 0)));
        assertEquals("attack", select(state(Kind.SPITTER, 0, 0), 0, .49f, 100).clip());
        assertEquals("idle", select(state(Kind.SPITTER, 0, 0), 0, .5f, 100).clip());
        assertTrue(released(state(Kind.HEART, 3, 1), state(Kind.HEART, 0, 0)));
        assertFalse(released(state(Kind.HEART, 3, 1), new State(Kind.HEART, 0, 0, 2, 40, 0, 0, 0)));
        assertEquals("idle", select(state(Kind.HEART, 0, 0), 0, .4f, 100).clip());
    }
    @ParameterizedTest @EnumSource(Kind.class)
    void hurtIsBoundedAndAttackHasPriority(Kind kind) {
        var s = new State(kind, 0, 0, 1, 0, 0, 5, 0);
        assertEquals("hurt", select(s, 0, 100, .2f).clip());
        assertNotEquals("hurt", select(s, 0, 100, .4f).clip());
        assertEquals("attack", select(s, 0, .1f, .2f).clip());
    }
    @Test void phaseAndEnrageAreIndependentOfLocalEntityAge() {
        var s = new State(Kind.HEART, 0, 0, 2, 20, 0, 0, 0);
        assertEquals("enrage", choose(s).clip()); assertEquals(.5f, choose(s).time());
        assertEquals("idle_phase2", choose(new State(Kind.HEART, 0, 0, 2, 0, 0, 0, 0)).clip());
    }
    @Test void loopWrapAndForwardDivePitch() {
        assertEquals(.25f, sample(new Selection("move", Timing.LOOP, 0, 1), 2.25f, 1));
        assertEquals(1, sample(new Selection("attack", Timing.SECONDS, 2, 1), 0, 1));
        assertEquals(0, flightPitch(.5, 0), 1e-6); assertTrue(flightPitch(.5, -1) > 0); assertTrue(flightPitch(.5, 1) < 0);
        assertEquals(0, sample(new Selection("idle", Timing.LOOP, 0, 1), 1, Float.NaN));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void promotedGlbHasEveryRuntimeClipAndOneSecondDeath(Kind kind) throws Exception {
        String name = "gloom_" + kind.name().toLowerCase(java.util.Locale.ROOT);
        var base = Path.of(System.getProperty("orimod.projectDir"));
        var bytes = Files.readAllBytes(base.resolve("src/main/resources/assets/orimod/models/entity/" + name + ".glb"));
        int length = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        var glb = JsonParser.parseString(new String(bytes, 20, length, StandardCharsets.UTF_8)).getAsJsonObject();
        var names = new HashSet<String>();
        for (var entry : glb.getAsJsonArray("animations")) {
            var animation = entry.getAsJsonObject(); names.add(animation.get("name").getAsString());
            if (animation.get("name").getAsString().equals("death")) {
                float end = 0;
                for (var sampler : animation.getAsJsonArray("samplers")) {
                    int input = sampler.getAsJsonObject().get("input").getAsInt();
                    end = Math.max(end, glb.getAsJsonArray("accessors").get(input).getAsJsonObject().getAsJsonArray("max").get(0).getAsFloat());
                }
                assertEquals(1, end, 1e-5, "Death must finish before vanilla removes the entity");
            }
        }
        assertTrue(names.containsAll(required(kind)), name + " missing " + required(kind));
        for (var image : glb.getAsJsonArray("images")) assertTrue(image.getAsJsonObject().getAsJsonObject("extras").has("resourceLocation"));
    }
}
