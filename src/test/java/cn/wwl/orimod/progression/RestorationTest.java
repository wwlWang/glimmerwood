package cn.wwl.orimod.progression;

import cn.wwl.orimod.world.*;
import cn.wwl.orimod.fx.RestorationDisplay;
import cn.wwl.orimod.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RestorationTest {
    // Historical eleven-shard save fixture: keep its original bits to verify migration.
    @Test void oldNbtDefaultsToNoWispsWithoutChangingEquipmentOrRewards(){var tag=new CompoundTag();tag.putInt("owned_shards",2047);tag.putInt("equipped_shards",129);tag.putInt("light",123);var rewards=new CompoundTag();rewards.putInt("purify/minecraft:overworld/12",1);tag.put("rewards",rewards);
        var p=SpiritProgress.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();assertEquals(0,p.wisps());assertEquals(2047,p.ownedShards());assertEquals(129,p.equippedShards());assertEquals(123,p.light());assertEquals(1,WillWisp.purifications(p));}
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7}) void everyWispCombinationSurvivesNbtAndProgressUpdates(int bits){
        var p=SpiritProgress.EMPTY.withWisps(bits);p=p.withLight(9).withOwned(Shard.MASK).withEquipped(129).withClaims(3).withCapacity(2).withRewards(Map.of("tree/site",1));
        assertEquals(bits,p.wisps());assertEquals(p,SpiritProgress.CODEC.parse(NbtOps.INSTANCE,SpiritProgress.CODEC.encodeStart(NbtOps.INSTANCE,p).getOrThrow()).getOrThrow());}
    @Test void futureBitsDoNotPolluteCurrentWispMask(){assertEquals(7,SpiritProgress.EMPTY.withWisps(255).wisps());}
    @Test void purificationsCountOnlyDistinctPositivePersonalReceiptsAndCapAtThree(){var r=new HashMap<String,Integer>();r.put("tree/1",1);r.put("purify/1",0);assertEquals(0,WillWisp.purifications(SpiritProgress.EMPTY.withRewards(r)));for(int i=0;i<6;i++){r.put("purify/"+i,1);assertEquals(Math.min(i+1,3),WillWisp.purifications(SpiritProgress.EMPTY.withRewards(r)));}}
    @Test void ritualRequiresSpiritAllWispsAndNoPriorCompletion(){assertNull(RestorationRules.refusal(true,true,7,false,false));assertEquals("dormant",RestorationRules.refusal(false,true,7,false,false));assertEquals("dormant",RestorationRules.refusal(true,false,7,false,false));assertEquals("wisps",RestorationRules.refusal(true,true,3,false,false));assertEquals("complete",RestorationRules.refusal(true,true,7,true,false));assertEquals("busy",RestorationRules.refusal(true,true,7,false,true));}
    @Test void pendingLedgerRoundTripsWithoutReplacingFirstAltarOnRepeatBossKill(){var data=new RestorationData();var owner=UUID.randomUUID();var a=data.offer(owner,WillWisp.HEART,"minecraft:overworld",new BlockPos(1,64,2));assertEquals(a,data.offer(owner,WillWisp.HEART,"minecraft:overworld",new BlockPos(90,65,70)));var other=UUID.randomUUID();data.offer(other,WillWisp.HEART,"minecraft:overworld",a.source());var copy=RestorationData.load(data.save(new CompoundTag(),null),null);assertEquals(a,copy.claim(owner,WillWisp.HEART));assertEquals(2,copy.claims().size());copy.remove(owner,WillWisp.HEART);assertEquals(1,copy.claims().size());}
    @Test void globalFlagSurvivesRestartAndResetInvalidatesOfflineEpoch(){var d=new RestorationData();int epoch=d.epoch();d.complete();var copy=RestorationData.load(d.save(new CompoundTag(),null),null);assertTrue(copy.restored());assertEquals(epoch,copy.epoch());copy.reset();assertFalse(copy.restored());assertNotEquals(epoch,copy.epoch());}
    @Test void restoredWavesRetainOldRandomCompositionWhenInactiveAndUseRoundedHalfSpittersWhenActive(){for(int extra=0;extra<=1;extra++)for(float beetle:new float[]{0,1})for(float hawk:new float[]{0,1}){var old=GloomTideRules.wave(extra,beetle,hawk);assertEquals(old,GloomTideRules.wave(extra,beetle,hawk,false));var small=GloomTideRules.wave(extra,beetle,hawk,true);assertEquals((old.size()+1)/2,small.size());assertTrue(small.stream().allMatch(m->m==GloomTideRules.WaveMob.SPITTER));}}
    private static RestorationPayload session(){return new RestorationPayload(false,false,Optional.of(UUID.randomUUID()),BlockPos.ZERO,net.minecraft.world.phys.Vec3.ZERO,300,List.of());}
    @Test void escapeOutsideSessionFallsThroughToVanillaPause(){var display=new RestorationDisplay();assertFalse(display.escape(true,256,1));display.receive(session());assertTrue(display.escape(true,256,1));assertFalse(display.escape(true,256,0));assertFalse(display.escape(true,87,1));display.receive(new RestorationPayload(true,true,Optional.empty(),BlockPos.ZERO,net.minecraft.world.phys.Vec3.ZERO,0,List.of()));assertFalse(display.escape(true,256,1));}
    @Test void disconnectWithoutEndPacketImmediatelyRestoresEscape(){var display=new RestorationDisplay();display.receive(session());assertFalse(display.escape(false,256,1));display.clear();assertFalse(display.active());assertFalse(display.escape(true,256,1));}
    @Test void lostSessionLeaseCannotLeaveInputLocked(){var display=new RestorationDisplay();display.receive(session());for(int i=0;i<59;i++)display.tick();assertTrue(display.active());display.tick();assertFalse(display.active());assertFalse(display.escape(true,256,1));}
    @Test void serverRefreshKeepsCeremonyAliveAndCannotExtendBeyondRemainingTime(){var display=new RestorationDisplay();var p=session();display.receive(p);for(int i=0;i<40;i++)display.tick();assertEquals(260,display.remaining());display.receive(new RestorationPayload(false,false,p.session(),BlockPos.ZERO,net.minecraft.world.phys.Vec3.ZERO,250,List.of()));assertEquals(250,display.remaining());assertTrue(display.active());}
}
