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

    @Test void channelVersionBumpedForPhase() { assertEquals("4", SpiritFxPayload.VERSION); }

    @ParameterizedTest @ValueSource(ints = {-1, 16, 99})
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

    private static void body(RegistryFriendlyByteBuf buffer) { buffer.writeDouble(0); buffer.writeDouble(0); buffer.writeDouble(0); buffer.writeVarInt(-1); }

    private static void assertRejected(Consumer<RegistryFriendlyByteBuf> write) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { write.accept(buffer); assertThrows(DecoderException.class, () -> SpiritFxPayload.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
