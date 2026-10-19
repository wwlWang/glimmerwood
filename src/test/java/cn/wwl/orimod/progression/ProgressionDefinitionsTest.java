package cn.wwl.orimod.progression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionDefinitionsTest {
    @ParameterizedTest @CsvSource({"dash,20,-", "bash,30,dash", "glide,30,bash", "grapple,40,glide", "stomp,40,grapple", "bow,30,dash", "launch,40,stomp", "light_burst,40,bash"})
    void abilityCostAndPrerequisite(String id, int cost, String prerequisite) {
        var ability = Ability.byId(id); assertNotNull(ability);
        assertEquals(cost, ability.cost);
        assertEquals(prerequisite.equals("-") ? null : Ability.byId(prerequisite), ability.prerequisite);
    }
    @ParameterizedTest @CsvSource({"capacity,dash", "recovery,dash", "gathering,-", "resilience,bash", "air_control,glide"})
    void shardPrerequisite(String id, String prerequisite) {
        var shard = Shard.byId(id); assertNotNull(shard);
        assertEquals(prerequisite.equals("-") ? null : Ability.byId(prerequisite), shard.prerequisite);
    }
    @ParameterizedTest @CsvSource({"0,30", "1,45", "2,60"})
    void energyUpgradePrices(int level, int expected) {
        assertEquals(expected, SpiritProgression.capacityCost(SpiritProgress.EMPTY.withCapacity(level)));
    }
    @Test void abilityGraphHasNoCyclesOrCollidingBits() {
        int bits = 0;
        for (var ability : Ability.values()) {
            assertEquals(1, Integer.bitCount(ability.bit)); assertEquals(0, bits & ability.bit); bits |= ability.bit;
            var seen = new HashSet<Ability>();
            for (var at = ability; at != null; at = at.prerequisite) assertTrue(seen.add(at), "Cyclic prerequisite: " + ability);
        }
    }
    @Test void shardBitsFitSavedMask() {
        int bits = 0;
        for (var shard : Shard.values()) { assertEquals(1, Integer.bitCount(shard.bit)); assertEquals(0, bits & shard.bit); bits |= shard.bit; }
        assertEquals(bits, SpiritProgress.EMPTY.withOwned(bits).ownedShards());
    }
    @Test void unknownIdentifiersAreRejected() { assertNull(Ability.byId("flight")); assertNull(Shard.byId("infinite_energy")); }
}
