package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.SpiritFxPayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SpiritFxPayloadTest {
    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void roundTrip(SpiritFxKind kind) {
        for (var phase : SpiritFxPhase.values()) {
            if (!kind.persistent() && (phase == SpiritFxPhase.START || phase == SpiritFxPhase.STOP)) continue;
            var original = new SpiritFxPayload(kind, phase, 12.5, -63.25, 1e6 + .125, kind.ordinal() == 0 ? SpiritFxPayload.NO_SOURCE : 4321);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                SpiritFxPayload.CODEC.encode(buffer, original);
                assertEquals(original, SpiritFxPayload.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test void burstConstructorIsBurst() {
        assertEquals(SpiritFxPhase.BURST, new SpiritFxPayload(SpiritFxKind.DASH, 0, 0, 0, 1).phase());
    }

    @Test void channelVersionBumpedForSlamScale() { assertEquals("11", SpiritFxPayload.VERSION); }
    @Test void slamScaleRoundTripsAndCannotExceedItsCap() {
        var original = new SpiritFxPayload(SpiritFxKind.STOMP_IMPACT, SpiritFxPhase.BURST, 1, 2, 3, 42, 1.375f);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SpiritFxPayload.CODEC.encode(buffer, original);
            assertEquals(original, SpiritFxPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
        assertEquals(1.375f, new SpiritFxPayload(SpiritFxKind.STOMP_IMPACT, SpiritFxPhase.BURST, 0, 0, 0, 1, 99).scale());
        assertEquals(1, new SpiritFxPayload(SpiritFxKind.STOMP_IMPACT, SpiritFxPhase.BURST, 0, 0, 0, 1, Float.NaN).scale());
        assertEquals(1, new SpiritFxPayload(SpiritFxKind.DASH, SpiritFxPhase.BURST, 0, 0, 0, 1, 2).scale());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 25, 99})
    void unknownKindRejected(int id) {
        assertRejected(buffer -> { buffer.writeVarInt(id); buffer.writeByte(0); body(buffer); });
    }

    @ParameterizedTest @ValueSource(ints = {4, 9, -1})
    void unknownPhaseRejected(int phase) {
        assertRejected(buffer -> { buffer.writeVarInt(SpiritFxKind.GLIDE.wireId()); buffer.writeByte(phase); body(buffer); });
    }

    @Test void startOnOneShotRejected() {
        assertRejected(buffer -> { buffer.writeVarInt(SpiritFxKind.DASH.wireId()); buffer.writeByte(SpiritFxPhase.START.wireId()); body(buffer); });
    }
    @Test void treeBlessingCannotBeUsedAsAPersistentLoop() {
        assertRejected(buffer -> { buffer.writeVarInt(SpiritFxKind.TREE_BLESSING.wireId()); buffer.writeByte(SpiritFxPhase.START.wireId()); body(buffer); });
    }

    @Test void purifyCannotBeUsedAsAPersistentLoop() {
        assertRejected(buffer -> { buffer.writeVarInt(SpiritFxKind.PURIFY.wireId()); buffer.writeByte(SpiritFxPhase.START.wireId()); body(buffer); });
    }
    @Test void oldVersionSevenDecoderRejectsPurifyKind() {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            SpiritFxPayload.CODEC.encode(b,new SpiritFxPayload(SpiritFxKind.PURIFY,0,0,0,-1));
            // Preserve the v7 fixed id range; handshake separately rejects channel version 7.
            assertThrows(DecoderException.class,()->{int id=b.readVarInt();if(id<0 || id>18)throw new DecoderException("Unknown spirit effect kind "+id);});
        } finally {b.release();}
    }
    @ParameterizedTest @ValueSource(ints={20,21})
    void versionEightDecoderRejectsNewKinds(int id){
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{SpiritFxPayload.CODEC.encode(b,new SpiritFxPayload(SpiritFxKind.byWireId(id),0,0,0,-1));
            assertThrows(DecoderException.class,()->{int oldId=b.readVarInt();if(oldId<0||oldId>19)throw new DecoderException("Unknown spirit effect kind "+oldId);});
        }finally{b.release();}
    }
    @ParameterizedTest @ValueSource(ints={22,23,24})
    void versionNineDecoderRejectsRegenerateKinds(int id){
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { SpiritFxPayload.CODEC.encode(b,new SpiritFxPayload(SpiritFxKind.byWireId(id),0,0,0,-1));
            assertThrows(DecoderException.class,()->{int old=b.readVarInt();if(old<0||old>21)throw new DecoderException("Unknown spirit effect kind "+old);});
        } finally { b.release(); }
    }
    private static void body(RegistryFriendlyByteBuf buffer) { buffer.writeDouble(0); buffer.writeDouble(0); buffer.writeDouble(0); buffer.writeVarInt(-1); }

    private static void assertRejected(Consumer<RegistryFriendlyByteBuf> write) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { write.accept(buffer); assertThrows(DecoderException.class, () -> SpiritFxPayload.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
