package cn.wwl.orimod.progression;

import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShardSlotsTest {
    @Test void oldSaveEquipmentReceivesStableOrderedSlots() {
        // Historical eleven-shard save, intentionally not the current all-owned mask.
        var p = SpiritProgress.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"owned_shards\":2047,\"equipped_shards\":521}")).getOrThrow();
        assertEquals(List.of(1, 8, 512, 0), p.shardSlots()); assertEquals(521, p.equippedShards());
    }
    @Test void movingToEmptySlotKeepsExactDestinationThroughSaveAndOtherEdits() {
        var p = SpiritProgress.EMPTY.withOwned(Shard.MASK).withEquipped(Shard.MAGNET.bit).withSlot(3, Shard.MAGNET.bit);
        assertEquals(List.of(0, 0, 0, Shard.MAGNET.bit), p.shardSlots());
        p = p.withLight(9).withClaims(15).withWisps(7).withCapacity(2);
        var encoded = SpiritProgress.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
        assertEquals(p, SpiritProgress.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
    @Test void draggingEquippedShardOntoAnotherSwapsWithoutDroppingEither() {
        var p = SpiritProgress.EMPTY.withOwned(15).withEquipped(3).withSlot(1, 1);
        assertEquals(List.of(2, 1, 0, 0), p.shardSlots()); assertEquals(3, p.equippedShards());
    }
    @Test void draggingFromCollectionReplacesOnlyTheDestination() {
        var p = SpiritProgress.EMPTY.withOwned(15).withEquipped(3).withSlot(1, 4);
        assertEquals(List.of(1, 4, 0, 0), p.shardSlots()); assertEquals(5, p.equippedShards()); assertEquals(15, p.ownedShards());
    }
    @Test void corruptSlotsCannotInventOrDuplicateEquipment() {
        assertEquals(List.of(1, 2, 4, 0), ShardSlots.normalize(List.of(1, 1, 3, Integer.highestOneBit(Shard.MASK) << 1), 7));
        var p = SpiritProgress.EMPTY.withOwned(1).withSlot(0, 2);
        assertEquals(0, p.equippedShards()); assertEquals(List.of(0, 0, 0, 0), p.shardSlots());
    }
    @Test void unequippingAndLosingOwnershipClearOnlyAffectedSlots() {
        var p = SpiritProgress.EMPTY.withOwned(7).withEquipped(7).withEquipped(5);
        assertEquals(List.of(1, 0, 4, 0), p.shardSlots());
        assertEquals(List.of(0, 0, 4, 0), p.withOwned(4).shardSlots());
    }
}
