package cn.wwl.orimod.progression;

import cn.wwl.orimod.progression.CompanionHints.Hint;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CompanionHintsTest {
    private static final int SKILLS = Ability.GLIDE.bit | Ability.GRAPPLE.bit;
    @Test void treeHintFollowsBossAltarAndReachesTwentyFourBlocks() {
        assertEquals(24, Hint.SPIRIT_TREE.radius);
        var both = EnumSet.of(Hint.BOSS_ALTAR, Hint.SPIRIT_TREE);
        assertEquals(Hint.BOSS_ALTAR, CompanionHints.pick(Set.of(), both, 0, 0, Long.MIN_VALUE));
        assertEquals(Hint.SPIRIT_TREE, CompanionHints.pick(Set.of("boss_altar"), both, 0, 0, Long.MIN_VALUE));
    }

    @Test void seenHintsAreSkippedAndDeclarationOrderWins() {
        var all = EnumSet.allOf(Hint.class);
        assertEquals(Hint.UPDRAFT, CompanionHints.pick(Set.of(), all, SKILLS, 0, Long.MIN_VALUE));
        assertEquals(Hint.BASH_ANCHOR, CompanionHints.pick(Set.of("updraft"), all, SKILLS, 200, 0));
        assertNull(CompanionHints.pick(Set.of("low_energy"), EnumSet.of(Hint.LOW_ENERGY), SKILLS, 500, 0));
        assertNull(CompanionHints.pick(Set.of(), EnumSet.noneOf(Hint.class), SKILLS, 500, 0));
    }

    @Test void gapAndClockRollbackDoNotSendEarly() {
        assertNull(CompanionHints.pick(Set.of(), EnumSet.allOf(Hint.class), SKILLS, 299, 100));
        assertNotNull(CompanionHints.pick(Set.of(), EnumSet.allOf(Hint.class), SKILLS, 300, 100));
        assertNull(CompanionHints.pick(Set.of(), EnumSet.allOf(Hint.class), SKILLS, 99, 100));
    }

    @ParameterizedTest @CsvSource({"14,100,200,0,true", "15,100,200,0,false", "14,100,201,0,false",
            "14,100,0,1,false", "0,100,10,10,true", "0,0,10,10,false"})
    void lowEnergyRequiresRecentSpendAndStrictThreshold(float energy, float maximum, long now, long last, boolean expected) {
        assertEquals(expected, CompanionHints.lowEnergy(energy, maximum, now, last));
    }

    @Test void newSessionCannotInventAnEnergySpend() {
        assertFalse(CompanionHints.lowEnergy(0, 100, 0, new EnergyState().lastHintSpendAt));
    }

    @Test void oldSaveAndMixedRewardKeysRemainCompatible() {
        var old = SpiritProgress.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"light\":42,\"rewards\":{\"ruin/forest\":2}}")).getOrThrow();
        assertTrue(CompanionHints.seen(old).isEmpty());
        var progress = old.withRewards(Map.of("ruin/forest", 2, "hint/updraft", 1, "hint/future", 1));
        assertEquals(Set.of("updraft", "future"), CompanionHints.seen(progress));
        var encoded = SpiritProgress.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow();
        assertEquals(progress, SpiritProgress.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test void lockedWordingMapsToTheSameCondition() {
        assertEquals("updraft_locked", CompanionHints.wireId(Hint.UPDRAFT, 0));
        assertEquals("updraft", CompanionHints.wireId(Hint.UPDRAFT, Ability.GLIDE.bit));
        assertEquals("grapple_flower_locked", CompanionHints.wireId(Hint.GRAPPLE_FLOWER, 0));
        assertEquals("grapple_flower", CompanionHints.wireId(Hint.GRAPPLE_FLOWER, Ability.GRAPPLE.bit));
        assertEquals(Hint.UPDRAFT, Hint.byWireId("updraft_locked"));
        assertNull(Hint.byWireId("low_energy_locked"));
    }

    @ParameterizedTest @CsvSource({"UPDRAFT,GLIDE", "GRAPPLE_FLOWER,GRAPPLE"})
    void lockedAndLearnedHintsEachHaveTheirOwnSeenRecord(Hint hint, Ability ability) {
        String locked = hint.id + "_locked";
        var conditions = EnumSet.of(hint);
        assertEquals(hint, CompanionHints.pick(Set.of(), conditions, 0, 0, Long.MIN_VALUE));
        var seen = CompanionHints.seen(SpiritProgress.EMPTY.withRewards(Map.of("hint/" + locked, 1)));
        assertEquals(Set.of(locked), seen);
        assertNull(CompanionHints.pick(seen, conditions, 0, 200, 0), "locked wording is only sent once");
        assertNull(CompanionHints.pick(seen, conditions, ability.bit, 199, 0), "learning keeps the shared cooldown");
        assertEquals(hint, CompanionHints.pick(seen, conditions, ability.bit, 200, 0), "learning enables the usage hint");
        assertEquals(hint, CompanionHints.pick(Set.of(hint.id), conditions, 0, 200, 0), "normal record does not consume locked wording");
        var both = CompanionHints.seen(SpiritProgress.EMPTY.withRewards(Map.of("hint/" + locked, 1, "hint/" + hint.id, 1)));
        assertNull(CompanionHints.pick(both, conditions, 0, 400, 200));
        assertNull(CompanionHints.pick(both, conditions, ability.bit, 400, 200));
    }

    @Test void bothLanguagesCoverAllHintsWithShortCompanionVoice() throws Exception {
        var root = Path.of(System.getProperty("orimod.projectDir", ".")).resolve("src/main/resources/assets/orimod/lang");
        var zh = JsonParser.parseString(Files.readString(root.resolve("zh_cn.json"))).getAsJsonObject();
        var en = JsonParser.parseString(Files.readString(root.resolve("en_us.json"))).getAsJsonObject();
        var ids = new java.util.ArrayList<String>();
        for (var hint : Hint.values()) ids.add(hint.id);
        ids.addAll(java.util.List.of("updraft_locked", "grapple_flower_locked"));
        for (var id : ids) {
            String chinese = zh.get("hint.orimod." + id).getAsString(), english = en.get("hint.orimod." + id).getAsString();
            assertTrue(chinese.startsWith("萤："), id);
            assertTrue(chinese.codePoints().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN).count() <= 30, id);
            assertTrue(english.startsWith("Glimmer: "), id);
        }
    }
}
