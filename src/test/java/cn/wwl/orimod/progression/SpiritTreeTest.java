package cn.wwl.orimod.progression;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpiritTreeTest {
    @Test void giftsFollowCostPrerequisitesAndStableTies() {
        var expected = List.of(Ability.DASH, Ability.BASH, Ability.GLIDE, Ability.BOW, Ability.CLIMB,
                Ability.WATER_DASH, Ability.GRAPPLE, Ability.STOMP, Ability.LAUNCH, Ability.LIGHT_BURST,
                Ability.CHARGE_FLAME, Ability.SMASH, Ability.FLASH, Ability.SPEAR);
        int mask = 0;
        for (var ability : expected) {
            assertEquals(ability, SpiritTree.gift(mask));
            mask |= ability.bit;
        }
        assertNull(SpiritTree.gift(mask));
    }
    @Test void everyOldOrPartialMaskGetsTheCheapestEligibleSkill() {
        int all = Arrays.stream(Ability.values()).mapToInt(a -> a.bit).reduce(0, (a, b) -> a | b);
        for (int mask = 0; mask <= all; mask++) {
            var gift = SpiritTree.gift(mask);
            if (mask == all) { assertNull(gift); continue; }
            assertNotNull(gift);
            assertEquals(0, gift.bit & mask);
            assertTrue(gift.prerequisite == null || (mask & gift.prerequisite.bit) != 0);
            for (var other : Ability.values()) if ((mask & other.bit) == 0
                    && (other.prerequisite == null || (mask & other.prerequisite.bit) != 0))
                assertTrue(gift.cost < other.cost || gift.cost == other.cost && gift.ordinal() <= other.ordinal());
        }
    }
    @Test void unknownBitsDoNotUnlockSkillsAndFullMaskGivesLight() {
        assertEquals(Ability.DASH, SpiritTree.gift(1 << 24));
        assertNull(SpiritTree.gift(-1));
        assertEquals(100, SpiritTree.LIGHT_GIFT);
    }
    @Test void siteRewardsRoundTripWithoutChangingOldFields() {
        var progress = SpiritProgress.EMPTY.withLight(45).withOwned(63).withEquipped(3)
                .withRewards(Map.of("ruin/forest", 2, "tree/minecraft:overworld/-3/67/15", 1));
        var encoded = SpiritProgress.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow();
        assertEquals(progress, SpiritProgress.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
