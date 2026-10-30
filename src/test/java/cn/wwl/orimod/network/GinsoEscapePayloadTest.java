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
    @Test void oldAndFutureProtocolRejectedBeforeFields(){for(int version:new int[]{0,1,255}){var b=buf();try{b.writeByte(version);assertTrue(assertThrows(DecoderException.class,()->GinsoEscapePayload.CODEC.decode(b)).getMessage().contains("expected 2"));}finally{b.release();}}}
    @Test void nanHugeBoxAndUnboundedCountRejected(){
        assertThrows(IllegalArgumentException.class,()->new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.PREPARE,BlockPos.ZERO,0,Double.NaN,0,1000,0,0,List.of()));
        assertThrows(IllegalArgumentException.class,()->new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.PREPARE,BlockPos.ZERO,0,91,0,1000,0,0,List.of(new GinsoEscapeLayout.Box(-10000,90,0,2,110,2))));
        for(int n:new int[]{-1,GinsoEscapePayload.MAX_BOXES+1,Integer.MAX_VALUE}){var b=buf();try{b.writeByte(2);b.writeUUID(UUID.randomUUID());b.writeByte(1);b.writeBlockPos(BlockPos.ZERO);b.writeByte(0);b.writeDouble(91);b.writeByte(1);b.writeDouble(1);for(int i=0;i<4;i++)b.writeVarInt(0);b.writeVarInt(n);assertThrows(DecoderException.class,()->GinsoEscapePayload.CODEC.decode(b));}finally{b.release();}}
    }
    @Test void truncatedPacketRejected(){var b=buf();try{b.writeByte(2);assertThrows(DecoderException.class,()->GinsoEscapePayload.CODEC.decode(b));}finally{b.release();}}
    @Test void bottomStartAndVersionedMultiplierRoundTrip(){
        var p=new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.PREPARE,BlockPos.ZERO,0,1,0,7200,0,0,List.of(new GinsoEscapeLayout.Box(-4,1,-4,5,98,5)),2,.7);var b=buf();
        try{GinsoEscapePayload.CODEC.encode(b,p);assertEquals(p,GinsoEscapePayload.CODEC.decode(b));}finally{b.release();}
        assertThrows(IllegalArgumentException.class,()->new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.PREPARE,BlockPos.ZERO,0,1,0,7200,0,0,List.of(),2,Double.NaN));
        for(double invalid:new double[]{.49,2.01})assertThrows(IllegalArgumentException.class,()->new GinsoEscapePayload(UUID.randomUUID(),GinsoEscape.Phase.PREPARE,BlockPos.ZERO,0,-4,0,7200,0,0,List.of(),2,invalid));
    }
    private static RegistryFriendlyByteBuf buf(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
}
