package cn.wwl.orimod.network;
import cn.wwl.orimod.world.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GinsoEscapePayloadTest {
    @Test void startAndSmallClockUpdateRoundTrip(){for(var boxes:List.of(List.<GinsoEscapeLayout.Box>of(),List.of(new GinsoEscapeLayout.Box(-2,90,-3,5,107,7)))){
        var p=new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.RUNNING,new BlockPos(5048,71,4312),2,91.5,231,1300,11,0,boxes);var b=buf();
        try{GinsoEscapePayload.CODEC.encode(b,p);assertEquals(p,GinsoEscapePayload.CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }}
    @Test void oldAndFutureProtocolRejectedBeforeFields(){for(int version:new int[]{0,2,255}){var b=buf();try{b.writeByte(version);assertTrue(assertThrows(DecoderException.class,()->GinsoEscapePayload.CODEC.decode(b)).getMessage().contains("expected 1"));}finally{b.release();}}}
    @Test void nanHugeBoxAndUnboundedCountRejected(){
        assertThrows(IllegalArgumentException.class,()->new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.PREPARE,BlockPos.ZERO,0,Double.NaN,0,1000,0,0,List.of()));
        assertThrows(IllegalArgumentException.class,()->new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.PREPARE,BlockPos.ZERO,0,91,0,1000,0,0,List.of(new GinsoEscapeLayout.Box(-10000,90,0,2,110,2))));
        for(int n:new int[]{-1,1025,Integer.MAX_VALUE}){var b=buf();try{b.writeByte(1);b.writeUUID(UUID.randomUUID());b.writeByte(1);b.writeBlockPos(BlockPos.ZERO);b.writeByte(0);b.writeDouble(91);for(int i=0;i<4;i++)b.writeVarInt(0);b.writeVarInt(n);assertThrows(DecoderException.class,()->GinsoEscapePayload.CODEC.decode(b));}finally{b.release();}}
    }
    @Test void truncatedPacketRejected(){var b=buf();try{b.writeByte(1);assertThrows(DecoderException.class,()->GinsoEscapePayload.CODEC.decode(b));}finally{b.release();}}
    private static RegistryFriendlyByteBuf buf(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
}
