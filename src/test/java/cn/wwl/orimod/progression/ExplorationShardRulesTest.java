package cn.wwl.orimod.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationShardRulesTest {
    @Test void breathHalvesLossWithoutRoundingToImmunity() {
        var breath = new ExplorationShardRules.Breath(); int total = 0;
        for (int i = 0; i < 300; i++) total += breath.consume(1, true);
        assertEquals(150, total);
    }
    @Test void respirationSkippedTicksDoNotConsumeAirOrResetCarry() {
        var breath = new ExplorationShardRules.Breath();
        assertEquals(0, breath.consume(1, true));
        for (int i = 0; i < 10; i++) assertEquals(0, breath.consume(0, true));
        assertEquals(1, breath.consume(1, true));
        assertEquals(3, breath.consume(6, true));
    }
    @Test void leavingWaterOrUnequippingClearsBreathCarry() {
        var breath = new ExplorationShardRules.Breath();
        breath.consume(1, true); assertEquals(1, breath.consume(1, false));
        assertEquals(0, breath.consume(1, true)); assertEquals(1, breath.consume(1, true));
    }
    @Test void darkHysteresisDoesNotFlickerAtEntranceThreshold() {
        boolean dark = ExplorationShardRules.dark(false, 5); assertTrue(dark);
        for (int light : new int[]{6, 5, 7, 8, 6}) { dark = ExplorationShardRules.dark(dark, light); assertTrue(dark); }
        assertFalse(ExplorationShardRules.dark(dark, 9));
        assertFalse(ExplorationShardRules.dark(false, 8));
    }
    @Test void brightnessRampsAndClampsInBothDirections() {
        float strength = 0;
        for (int i = 0; i < 40; i++) { float next = ExplorationShardRules.fade(strength, true); assertTrue(next >= strength); strength = next; }
        assertEquals(.55f, strength);
        for (int i = 0; i < 40; i++) strength = ExplorationShardRules.fade(strength, false);
        assertEquals(0, strength);
    }
    @Test void burningEndsFourTimesSoonerButLavaStillHurts() {
        int ticks = 300, elapsed = 0;
        while (ticks > 0) { ticks = ExplorationShardRules.fireTicks(ticks - 1); elapsed++; }
        assertEquals(75, elapsed); assertEquals(2, ExplorationShardRules.lavaDamage(4));
        assertEquals(0, ExplorationShardRules.fireTicks(0));
    }
    @Test void prospectingHasARealCooldownIncludingClockReset() {
        assertTrue(ExplorationShardRules.ready(0, Long.MIN_VALUE));
        assertFalse(ExplorationShardRules.ready(199, 100)); assertTrue(ExplorationShardRules.ready(200, 100));
        assertTrue(ExplorationShardRules.ready(5, 100));
    }
    @Test void newShardsHaveStableDistinctBitsAndBilingualDescriptions() throws Exception {
        int mask = 0;
        for (var shard : Shard.values()) { assertEquals(1, Integer.bitCount(shard.bit)); assertEquals(0, mask & shard.bit); mask |= shard.bit; }
        assertEquals(131071, mask); assertEquals(mask, Shard.MASK);
        for (var lang : new String[]{"en_us", "zh_cn"}) try (var stream = getClass().getResourceAsStream("/assets/orimod/lang/" + lang + ".json")) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var shard : Shard.values()) for (var suffix : new String[]{"", ".description"})
                assertFalse(json.get("shard.orimod." + shard.id() + suffix).getAsString().isBlank());
        }
    }
    @Test void dailyMerchantStockIncludesAllSixAndExcludesOwned() {
        int missing = Shard.MAGNET.bit | Shard.DEEP_BREATH.bit | Shard.LUMEN_SIGHT.bit
                | Shard.PROSPECTOR.bit | Shard.EMBER_SKIN.bit | Shard.FORAGER.bit;
        var found = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) found.addAll(MerchantRules.stock(i, Shard.MASK & ~missing));
        assertEquals(java.util.Set.of("magnet", "deep_breath", "lumen_sight", "prospector", "ember_skin", "forager"), found);
        assertTrue(MerchantRules.stock(0, Shard.MASK).isEmpty());
    }
}
