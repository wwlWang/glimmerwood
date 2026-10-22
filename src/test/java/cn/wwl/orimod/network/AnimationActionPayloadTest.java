package cn.wwl.orimod.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AnimationActionPayloadTest {
    @Test void rejectsInvalidHandByte() {
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { b.writeVarInt(-1); b.writeByte(0); b.writeByte(2);
            assertThrows(DecoderException.class, () -> AnimationActionPayload.CODEC.decode(b));
        } finally { b.release(); }
    }
    @ParameterizedTest @EnumSource(AnimationActionPayload.Action.class)
    void roundTripEachActionAndHand(AnimationActionPayload.Action action) {
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            for (boolean offhand : new boolean[]{false, true}) for (int entity : new int[]{-1, 48}) {
                b.clear(); var p = new AnimationActionPayload(entity, action, offhand);
                AnimationActionPayload.CODEC.encode(b, p); assertEquals(p, AnimationActionPayload.CODEC.decode(b));
                assertEquals(0, b.readableBytes());
            }
        } finally { b.release(); }
    }
    @ParameterizedTest @ValueSource(ints = {7, 100, 255})
    void rejectsUnknownAction(int value) {
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { b.writeVarInt(-1); b.writeByte(value); b.writeBoolean(false);
            assertThrows(DecoderException.class, () -> AnimationActionPayload.CODEC.decode(b));
        } finally { b.release(); }
    }
    @Test void rejectsInvalidEntityAndClientCannotRequestSpellEvents() {
        assertEquals("1", AnimationActionPayload.VERSION);
        assertFalse(AnimationActionPayload.Action.CAST_HOLD.clientRequest());
        assertFalse(AnimationActionPayload.Action.CAST_RELEASE.clientRequest());
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { b.writeVarInt(-2); b.writeByte(0); b.writeBoolean(false);
            assertThrows(DecoderException.class, () -> AnimationActionPayload.CODEC.decode(b));
        } finally { b.release(); }
    }
}
