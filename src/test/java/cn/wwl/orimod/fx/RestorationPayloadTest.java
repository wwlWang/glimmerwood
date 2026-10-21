package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.*;
import cn.wwl.orimod.progression.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class RestorationPayloadTest {
    @Test void activeSessionAndAllThreePendingCoordinatesRoundTrip(){var list=Arrays.stream(WillWisp.values()).map(w->new RestorationPayload.Pending(w,"minecraft:overworld",new BlockPos(12,64,w.bit))).toList();var p=new RestorationPayload(true,false,Optional.of(UUID.randomUUID()),new BlockPos(2,3,4),new net.minecraft.world.phys.Vec3(2.25,3,1.5),137,list);var b=buf();try{RestorationPayload.CODEC.encode(b,p);assertEquals(p,RestorationPayload.CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @ParameterizedTest @ValueSource(ints={0,2,255}) void incompatibleVersionRejectedClearly(int version){var b=buf();try{b.writeByte(version);assertTrue(assertThrows(DecoderException.class,()->RestorationPayload.CODEC.decode(b)).getMessage().contains("expected protocol 1"));}finally{b.release();}}
    @ParameterizedTest @ValueSource(ints={-1,4,Integer.MAX_VALUE}) void rejectsUnboundedPendingCount(int n){reject(b->{header(b,0);b.writeVarInt(n);});}
    @ParameterizedTest @ValueSource(ints={-1,301,Integer.MAX_VALUE}) void rejectsInvalidRemaining(int ticks){reject(b->{header(b,ticks);b.writeVarInt(0);});}
    @Test void rejectsInactiveSessionWithRemainingTime(){reject(b->{header(b,20);b.writeVarInt(0);});}
    @Test void rejectsUnknownWisp(){reject(b->{header(b,0);b.writeVarInt(1);b.writeByte(3);});}
    @Test void rejectsInvalidDimension(){reject(b->{header(b,0);b.writeVarInt(1);b.writeByte(0);b.writeUtf("bad dimension!");b.writeBlockPos(BlockPos.ZERO);});}
    @Test void rejectsDuplicateClaims(){reject(b->{header(b,0);b.writeVarInt(2);for(int i=0;i<2;i++){b.writeByte(0);b.writeUtf("minecraft:overworld");b.writeBlockPos(BlockPos.ZERO);}});}
    @Test void rejectsInvalidFlagAndTruncation(){reject(b->{b.writeByte(1);b.writeByte(2);});reject(b->b.writeByte(1));}
    @Test void previousCompanionDecoderRejectsEndingLine(){var b=buf();try{CompanionHintPayload.CODEC.encode(b,CompanionHintPayload.line(CompanionLines.Line.RESTORATION));assertThrows(DecoderException.class,()->{if(b.readUnsignedByte()!=0||b.readUnsignedByte()!=8)throw new DecoderException("expected protocol 8");});}finally{b.release();}}
    @Test void rejectsNonFiniteOrOutOfWorldLockOrigin(){for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,30_000_001})reject(b->{
        b.writeByte(1);b.writeBoolean(false);b.writeBoolean(false);b.writeBoolean(false);b.writeBlockPos(BlockPos.ZERO);b.writeDouble(x);b.writeDouble(0);b.writeDouble(0);b.writeVarInt(0);b.writeVarInt(0);
    });}
    @Test void skipSessionTokenFitsUnchangedProgressActionProtocol(){var token=UUID.randomUUID().toString().replace("-","");assertEquals(32,token.length());var p=new ProgressActionPayload("restoration_skip",token);var b=buf();try{ProgressActionPayload.CODEC.encode(b,p);assertEquals(p,ProgressActionPayload.CODEC.decode(b));}finally{b.release();}}
    private static void header(RegistryFriendlyByteBuf b,int remaining){b.writeByte(1);b.writeBoolean(false);b.writeBoolean(false);b.writeBoolean(false);b.writeBlockPos(BlockPos.ZERO);b.writeDouble(0);b.writeDouble(0);b.writeDouble(0);b.writeVarInt(remaining);}
    private static RegistryFriendlyByteBuf buf(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    private static void reject(Consumer<RegistryFriendlyByteBuf> write){var b=buf();try{write.accept(b);assertThrows(DecoderException.class,()->RestorationPayload.CODEC.decode(b));}finally{b.release();}}
}
