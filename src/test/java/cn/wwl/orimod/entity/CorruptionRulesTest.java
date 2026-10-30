package cn.wwl.orimod.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorruptionRulesTest {
    @Test void attachmentVersionRoundTripAndPreVersionCompatibility(){
        var state=new CorruptionState("minecraft:overworld",123L,200,true,12.5);
        var json=CorruptionState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,state).getOrThrow().getAsJsonObject();
        assertEquals(1,json.get("version").getAsInt());assertEquals(state,CorruptionState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow());
        json.remove("version");assertEquals(state,CorruptionState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow());
        json.addProperty("version",2);assertTrue(CorruptionState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).error().isPresent());
    }
    @Test void exposureRequiresTwoHundredLoadedTicks(){assertEquals(199,CorruptionRules.exposure(198,1,true,true));assertEquals(200,CorruptionRules.exposure(199,1,true,true));}
    @Test void leavingResetsAndChangingOwnerDoesNotCarryExposure(){assertEquals(0,CorruptionRules.exposure(199,1,true,false));assertEquals(1,CorruptionRules.exposure(199,1,false,true));}
    @Test void offlineTimeCannotCreateExposureBurst(){assertEquals(20,CorruptionRules.exposure(0,24000,true,true));assertEquals(10,CorruptionRules.exposure(10,-1,true,true));}
    @Test void energyDrainIsHalfPerSecondAndStopsAtZero(){assertEquals(19.5f,CorruptionRules.drain(20,.5f));assertEquals(0,CorruptionRules.drain(.2f,.5f));}
    @Test void noPeacefulBabyLeashOrRiderAttack(){assertFalse(CorruptionRules.attack(true,false,false,false,true,1));assertFalse(CorruptionRules.attack(false,true,false,false,true,1));assertFalse(CorruptionRules.attack(false,false,true,false,true,1));assertFalse(CorruptionRules.attack(false,false,false,true,true,1));}
    @Test void twelveBlockBoundaryAndEligiblePlayer(){assertTrue(CorruptionRules.attack(false,false,false,false,true,144));assertFalse(CorruptionRules.attack(false,false,false,false,true,144.01));assertFalse(CorruptionRules.attack(false,false,false,false,false,1));}
}
