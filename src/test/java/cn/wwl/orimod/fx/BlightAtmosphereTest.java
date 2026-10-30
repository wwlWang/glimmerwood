package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.BlightAtmospherePayload;
import cn.wwl.orimod.world.BlightMobs;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlightAtmosphereTest {
    @Test void edgeIsFourBlocksAndNeverExceedsPointThreeFive(){
        assertEquals(.35f,BlightAtmosphereRules.strength(44,48,false,0));assertEquals(.175f,BlightAtmosphereRules.strength(46,48,false,0),.00001f);
        assertEquals(0,BlightAtmosphereRules.strength(48,48,false,0));assertEquals(0,BlightAtmosphereRules.strength(52,48,false,0));
    }
    @Test void purificationErasesInnerAreaAndFadesWaveEdge(){
        assertEquals(0,BlightAtmosphereRules.strength(20,48,true,.5f));assertEquals(.175f,BlightAtmosphereRules.strength(26,48,true,.5f),.00001f);
        assertEquals(.35f,BlightAtmosphereRules.strength(30,48,true,.5f));assertEquals(0,BlightAtmosphereRules.strength(46,48,true,1));
    }
    @Test void fadeNeedsTwentyTicksAndClearsOnWorldSwitch(){var f=new BlightAtmosphereRules.Fade();for(int i=0;i<19;i++)f.tick(.35f);assertTrue(f.value(1)<.35f);f.tick(.35f);assertEquals(.35f,f.value(1),.00001);for(int i=0;i<20;i++)f.tick(0);assertEquals(0,f.value(1),.00001);f.tick(.35f);f.clear();assertEquals(0,f.value(1));}
    @Test void strongestOnlyAndVanillaMediaTakePriority(){assertEquals(.35f,BlightAtmosphereRules.combined(.35f,.3f,false));assertEquals(.3f,BlightAtmosphereRules.combined(.2f,.3f,false));assertEquals(0,BlightAtmosphereRules.combined(.35f,.35f,true));}
    @Test void changedThrottledBoundaryImmediateAndHeartbeat(){assertFalse(BlightAtmosphereRules.send(19,true,false));assertTrue(BlightAtmosphereRules.send(20,true,false));assertFalse(BlightAtmosphereRules.send(99,false,false));assertTrue(BlightAtmosphereRules.send(100,false,false));assertTrue(BlightAtmosphereRules.send(0,false,true));}
    @Test void mobBudgetsShareCoreAndGlobalCaps(){assertTrue(BlightMobs.room(2,31,40,39,20));assertFalse(BlightMobs.room(3,0,40,39,20));assertFalse(BlightMobs.room(0,32,40,39,20));assertFalse(BlightMobs.room(0,0,40,40,20));assertFalse(BlightMobs.room(0,0,40,39,21));}
    @Test void fourCorePacketIsNinetyBytes(){var cores=new ArrayList<BlightAtmospherePayload.Core>();for(int i=0;i<4;i++)cores.add(new BlightAtmospherePayload.Core(i,48,true,.5f));
        var p=new BlightAtmospherePayload(ResourceLocation.parse("minecraft:overworld"),1,cores);var b=buffer();try{BlightAtmospherePayload.CODEC.encode(b,p);assertEquals(90,b.readableBytes());assertEquals(p,BlightAtmospherePayload.CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @Test void emptyClearPayloadRoundTrips(){var b=buffer();try{var p=new BlightAtmospherePayload(ResourceLocation.parse("minecraft:the_nether"),2,List.of());BlightAtmospherePayload.CODEC.encode(b,p);assertEquals(p,BlightAtmospherePayload.CODEC.decode(b));}finally{b.release();}}
    @Test void rejectsTooManyDuplicateCoresAndNonFiniteValues(){var c=new BlightAtmospherePayload.Core(1,48,false,0);assertThrows(IllegalArgumentException.class,()->new BlightAtmospherePayload(ResourceLocation.parse("minecraft:overworld"),1,Collections.nCopies(5,c)));assertThrows(IllegalArgumentException.class,()->new BlightAtmospherePayload(ResourceLocation.parse("minecraft:overworld"),1,List.of(c,c)));assertThrows(IllegalArgumentException.class,()->new BlightAtmospherePayload.Core(0,Float.NaN,false,0));assertThrows(IllegalArgumentException.class,()->new BlightAtmospherePayload.Core(0,48,false,.1f));}
    @Test void rejectsBadPhaseCountAndTruncation(){for(int count:new int[]{1,5}){var b=buffer();try{b.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));b.writeVarLong(1);b.writeByte(count);if(count==1){b.writeLong(0);b.writeFloat(48);b.writeByte(2);b.writeFloat(0);}assertThrows(DecoderException.class,()->BlightAtmospherePayload.CODEC.decode(b));}finally{b.release();}}var b=buffer();try{assertThrows(DecoderException.class,()->BlightAtmospherePayload.CODEC.decode(b));}finally{b.release();}}
    private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
}
