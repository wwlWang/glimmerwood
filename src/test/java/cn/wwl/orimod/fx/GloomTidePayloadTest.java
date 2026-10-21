package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.GloomTidePayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class GloomTidePayloadTest {
    @Test void independentVersionOne() { assertEquals("1", GloomTidePayload.VERSION); }
    @Test void roundTripStartAndStop() {
        for (var packet : new GloomTidePayload[]{new GloomTidePayload(true,.35f),new GloomTidePayload(false,0)}) {
            var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try { GloomTidePayload.CODEC.encode(b,packet); assertEquals(packet,GloomTidePayload.CODEC.decode(b)); assertEquals(0,b.readableBytes()); }
            finally { b.release(); }
        }
    }
    @ParameterizedTest @ValueSource(ints={2,127,255}) void rejectsNonBoolean(int flag) { rejected(b->{ b.writeByte(flag); b.writeFloat(.35f); }); }
    @ParameterizedTest @ValueSource(floats={-.001f,1.001f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
    void rejectsInvalidIntensity(float intensity) { rejected(b->{b.writeByte(1);b.writeFloat(intensity);}); }
    @Test void rejectsIntensityOnStop() { rejected(b->{b.writeByte(0);b.writeFloat(.35f);}); }
    @Test void rejectsTruncatedHeader() { rejected(b->{}); }
    @Test void rejectsTruncatedFloat() { rejected(b->{b.writeByte(1);b.writeShort(0);}); }
    private static void rejected(Consumer<RegistryFriendlyByteBuf> write) {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { write.accept(b);assertThrows(DecoderException.class,()->GloomTidePayload.CODEC.decode(b)); }
        finally { b.release(); }
    }
}
