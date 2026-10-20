package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.CompanionHintPayload;
import cn.wwl.orimod.progression.CompanionHints.Hint;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class CompanionHintPayloadTest {
    @ParameterizedTest @EnumSource(Hint.class) void roundTripEveryHint(Hint hint) {
        roundTrip(new CompanionHintPayload(hint.id, hint.radius > 0 ? Optional.of(new BlockPos(-123, -64, 999)) : Optional.empty()));
    }
    @ParameterizedTest @ValueSource(strings = {"updraft_locked", "grapple_flower_locked"})
    void roundTripLockedVariants(String id) { roundTrip(new CompanionHintPayload(id, Optional.of(BlockPos.ZERO))); }

    @Test void explicitChannelVersion() { assertEquals("1", CompanionHintPayload.VERSION); }

    @ParameterizedTest @ValueSource(strings = {"", "unknown", "low_energy_locked", "hint/updraft", "../updraft"})
    void rejectsUnknownIds(String id) { rejected(b -> { b.writeUtf(id); b.writeByte(0); }); }

    @ParameterizedTest @ValueSource(ints = {2, 127, 255})
    void rejectsNonBooleanPositionFlag(int flag) { rejected(b -> { b.writeUtf("updraft"); b.writeByte(flag); b.writeBlockPos(BlockPos.ZERO); }); }

    @Test void rejectsMissingSpatialPosition() { rejected(b -> { b.writeUtf("updraft"); b.writeByte(0); }); }
    @Test void rejectsPositionOnPersonalHint() { rejected(b -> { b.writeUtf("low_energy"); b.writeByte(1); b.writeBlockPos(BlockPos.ZERO); }); }
    @Test void rejectsOverlongId() { rejected(b -> { b.writeUtf("a".repeat(33)); b.writeByte(0); }); }
    @Test void rejectsTruncatedPosition() { rejected(b -> { b.writeUtf("updraft"); b.writeByte(1); b.writeInt(0); }); }
    @Test void rejectsTruncatedHeader() { rejected(b -> b.writeByte(20)); }
    @ParameterizedTest @ValueSource(ints = {-30_000_001, 30_000_001})
    void rejectsOutOfWorldPosition(int coordinate) {
        rejected(b -> { b.writeUtf("updraft"); b.writeByte(1); b.writeBlockPos(new BlockPos(coordinate, 64, 0)); });
        rejected(b -> { b.writeUtf("updraft"); b.writeByte(1); b.writeBlockPos(new BlockPos(0, 64, coordinate)); });
    }

    @Test void mutablePositionIsSnapshotted() {
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        var payload = new CompanionHintPayload("updraft", Optional.of(pos));
        pos.set(4, 5, 6);
        assertEquals(new BlockPos(1, 2, 3), payload.pos().orElseThrow());
    }

    private static void roundTrip(CompanionHintPayload payload) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CompanionHintPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, CompanionHintPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    private static void rejected(Consumer<RegistryFriendlyByteBuf> write) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { write.accept(buffer); assertThrows(DecoderException.class, () -> CompanionHintPayload.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
