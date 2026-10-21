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

    @Test void explicitChannelVersion() { assertEquals("3", CompanionHintPayload.VERSION); }

    @ParameterizedTest @ValueSource(strings = {"", "unknown", "low_energy_locked", "hint/updraft", "../updraft"})
    void rejectsUnknownIds(String id) { rejected(b -> { b.writeUtf(id); b.writeByte(0); }); }

    @ParameterizedTest @ValueSource(ints = {2, 127, 255})
    void rejectsNonBooleanPositionFlag(int flag) { rejected(b -> { b.writeUtf("updraft"); b.writeByte(flag); b.writeBlockPos(BlockPos.ZERO); }); }

    @Test void rejectsMissingSpatialPosition() { rejected(b -> { b.writeUtf("updraft"); b.writeByte(0); }); }
    @Test void treeHintRequiresItsHeartPosition() { rejected(b -> { b.writeUtf("spirit_tree"); b.writeByte(0); }); }
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

    private static void header(RegistryFriendlyByteBuf b, int kind) { b.writeByte(0); b.writeByte(3); b.writeByte(kind); }
    @ParameterizedTest @EnumSource(cn.wwl.orimod.progression.CompanionLines.Line.class)
    void roundTripEveryLine(cn.wwl.orimod.progression.CompanionLines.Line line) { roundTrip(CompanionHintPayload.line(line)); }
    @ParameterizedTest @ValueSource(ints={2,127,255})
    void rejectsUnknownKind(int kind) { rejectedRaw(b -> { header(b,kind); b.writeUtf("tree_found"); b.writeByte(0); }); }
    @ParameterizedTest @ValueSource(ints={0,1,2,4,255})
    void rejectsOtherWireVersions(int version) { rejectedRaw(b -> { b.writeByte(0); b.writeByte(version); }); }
    @Test void rejectsOldVersionTwoPacketWithClearMessage() {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            b.writeUtf("low_energy"); b.writeByte(0);
            var error=assertThrows(DecoderException.class,()->CompanionHintPayload.CODEC.decode(b));
            assertTrue(error.getMessage().contains("expected protocol 3"));
        } finally { b.release(); }
    }
    @Test void versionTwoDecoderRejectsEveryNewPacketBeforeReadingItsBody() {
        for(var packet:new CompanionHintPayload[]{new CompanionHintPayload("low_energy",Optional.empty()),
                CompanionHintPayload.line(cn.wwl.orimod.progression.CompanionLines.Line.TREE_FOUND)}) {
            var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try {
                CompanionHintPayload.CODEC.encode(b,packet);
                // The v2 decoder reads UTF id then a strict 0/1 position flag. Preserve that old parser here.
                assertThrows(DecoderException.class,()->{
                    b.readUtf(32);int flag=b.readUnsignedByte();
                    if(flag>1)throw new DecoderException("Invalid companion hint position flag "+flag);
                });
            } finally { b.release(); }
        }
    }
    @Test void rejectsLineIdInHintKind() { rejected(b->{b.writeUtf("tree_found");b.writeByte(0);}); }
    @Test void rejectsHintIdInLineKind() { rejectedRaw(b->{header(b,1);b.writeUtf("low_energy");b.writeByte(0);}); }
    @Test void rejectsCoordinatesOnLine() { rejectedRaw(b->{header(b,1);b.writeUtf("tree_found");b.writeByte(1);b.writeBlockPos(BlockPos.ZERO);}); }
    @Test void rejectsTruncatedVersionHeader() { rejectedRaw(b->b.writeByte(0)); }
    private static void rejectedRaw(Consumer<RegistryFriendlyByteBuf> write) {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { write.accept(b);assertThrows(DecoderException.class,()->CompanionHintPayload.CODEC.decode(b)); }
        finally { b.release(); }
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
        try { header(buffer, 0); write.accept(buffer); assertThrows(DecoderException.class, () -> CompanionHintPayload.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
