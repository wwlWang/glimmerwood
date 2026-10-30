package cn.wwl.orimod.progression;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SpiritProgressTest {
    private static SpiritProgress decode(String json) {
        return SpiritProgress.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test void emptyOldSaveReceivesDefaults() { assertEquals(SpiritProgress.EMPTY, decode("{}")); }

    @Test void oldSaveRetainsKnownFields() {
        var value = decode("{\"light\":71,\"owned_shards\":5,\"equipped_shards\":4}");
        assertEquals(71, value.light()); assertEquals(5, value.ownedShards());
        assertEquals(4, value.equippedShards()); assertEquals(0, value.capacityLevel());
        assertEquals(SpiritProgress.VERSION, value.version());
    }

    @Test void allFieldsSurviveCodecRoundTrip() {
        var value = new SpiritProgress(1, 127, 31, 17, 3, 2, Map.of("overworld/12/66/-45", 2));
        var json = SpiritProgress.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, SpiritProgress.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test void unknownFieldsDoNotEraseKnownProgress() {
        var value = decode("{\"version\":5,\"light\":99,\"future_setting\":{\"x\":true}}");
        assertEquals(99, value.light()); assertEquals(5, value.version());
    }

    @ParameterizedTest @CsvSource({"-1,0", "0,0", "1,1", "999999,999999", "1000000,1000000", "2147483647,1000000"})
    void lightBoundsPreventOverflow(int input, int expected) {
        assertEquals(expected, SpiritProgress.EMPTY.withLight(input).light());
    }

    @ParameterizedTest @CsvSource({"-1,0", "0,0", "1,1", "3,3", "4,3", "2147483647,3"})
    void savedCapacityStaysInSupportedRange(int input, int expected) {
        assertEquals(expected, SpiritProgress.EMPTY.withCapacity(input).capacityLevel());
    }

    @Test void unknownShardBitsAreDiscarded() { assertEquals(2047, SpiritProgress.EMPTY.withOwned(4095).ownedShards()); }
    @Test void oldNbtRetainsAllSixShardBitsAndEquipment() {
        for (int equipped = 0; equipped <= 63; equipped++) {
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("owned_shards", 63); tag.putInt("equipped_shards", equipped); tag.putInt("light", 17);
            var value = SpiritProgress.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
            assertEquals(63, value.ownedShards()); assertEquals(equipped, value.equippedShards()); assertEquals(17, value.light());
            assertEquals(SpiritProgress.VERSION, value.version());
        }
    }
    @Test void allElevenShardsRoundTripThroughNbt() {
        var original = new SpiritProgress(1, 120, 2047, 2047, 3, 2, Map.of("hint/updraft", 1));
        var nbt = SpiritProgress.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, original).getOrThrow();
        assertEquals(original, SpiritProgress.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt).getOrThrow());
    }
    @Test void unownedShardsCannotRemainEquipped() {
        assertEquals(4, SpiritProgress.EMPTY.withOwned(5).withEquipped(6).equippedShards());
    }
    @Test void losingOwnershipUnequipsThatShard() {
        assertEquals(1, SpiritProgress.EMPTY.withOwned(3).withEquipped(3).withOwned(1).equippedShards());
    }
    @Test void unknownWeaponClaimsCannotConsumeOtherBits() { assertEquals(15, SpiritProgress.EMPTY.withClaims(255).claimedWeapons()); }
    @Test void oldClaimsKeepTheirMeaningAndNewReceiptsRoundTrip() {
        for (int claims : new int[]{0, 1, 2, 3, 4, 8, 15}) {
            var progress = SpiritProgress.EMPTY.withClaims(claims);
            var encoded = SpiritProgress.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, progress).getOrThrow();
            assertEquals(claims, SpiritProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow().claimedWeapons());
        }
    }
    @Test void rewardMapIsAnImmutableSnapshot() {
        var source = new HashMap<>(Map.of("first", 1));
        var value = SpiritProgress.EMPTY.withRewards(source); source.put("first", 9);
        assertEquals(1, value.rewards().get("first"));
        assertThrows(UnsupportedOperationException.class, () -> value.rewards().put("new", 1));
    }
}
