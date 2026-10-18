package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.SpiritFxPayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SpiritFxPayloadTest {
    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void roundTrip(SpiritFxKind kind) {
        var original = new SpiritFxPayload(kind, 12.5, -63.25, 1e6 + .125, kind.ordinal() == 0 ? SpiritFxPayload.NO_SOURCE : 4321);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SpiritFxPayload.CODEC.encode(buffer, original);
            assertEquals(original, SpiritFxPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @ParameterizedTest @ValueSource(ints = {-1, 5, 99})
    void unknownKindRejected(int id) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(id); buffer.writeDouble(0); buffer.writeDouble(0); buffer.writeDouble(0); buffer.writeVarInt(-1);
            assertThrows(DecoderException.class, () -> SpiritFxPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
