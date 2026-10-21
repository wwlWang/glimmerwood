package cn.wwl.orimod.world;
import cn.wwl.orimod.progression.Shard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.wwl.orimod.world.ShrineRules.*;
class ShrineRulesTest {
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8}) void exactWavesAndCappedPartyScaling(int players){
        int extra=Math.min(3,players-1);
        assertEquals(3+extra,Collections.frequency(wave(1,players),MobKind.SPITTER));
        assertEquals(2,Collections.frequency(wave(2,players),MobKind.BEETLE));assertEquals(2+extra,Collections.frequency(wave(2,players),MobKind.SPITTER));
        assertEquals(2,Collections.frequency(wave(3,players),MobKind.HAWK));assertEquals(1,Collections.frequency(wave(3,players),MobKind.BEETLE));assertEquals(2+extra,Collections.frequency(wave(3,players),MobKind.SPITTER));
    }
    @Test void boundariesAndParticipation(){assertFalse(timedOut(5999));assertTrue(timedOut(6000));assertTrue(allOut(List.of(Participant.DEAD,Participant.LEFT)));assertFalse(allOut(List.of(Participant.DEAD,Participant.ACTIVE)));assertTrue(rewardEligible(Participant.DEAD));assertFalse(rewardEligible(Participant.LEFT));assertThrows(IllegalArgumentException.class,()->wave(1,9));assertThrows(IllegalArgumentException.class,()->wave(0,1));}
    @ParameterizedTest @ValueSource(ints={0,1,63,128,1023,2047}) void rewardNeverDuplicatesAndRepeatsAreSmall(int owned){
        for(int seed=-100;seed<100;seed++){var r=reward(owned,false,seed);assertEquals(0,r.shard()&owned);assertEquals(owned==2047?200:50,r.light());if(owned!=2047)assertTrue(Arrays.stream(Shard.values()).anyMatch(s->s.bit==r.shard()));assertEquals(new Reward(0,20),reward(owned,true,seed));}
    }
    @Test void contractIsClosedInThreeDimensionsEvenIfEveryOrdinaryBlockIsBroken(){
        for(var rotation:Rotation.values()){
            var c=ShrineContract.at(new RuinPlacement(new BlockPos(700,100,-1000),rotation));var cells=new HashSet<>(c.wards());
            assertEquals(c.wards().size(),cells.size());assertTrue(cells.size()>1000);
            assertFalse(escapes(c.altar().above(),cells));
            // Regression: the roadmap's original open-top perimeter does let flyers escape.
            cells.removeIf(p->p.getY()==c.altar().getY()+9);assertTrue(escapes(c.altar().above(),cells));
            var restored=ShrineContract.load(c.save());assertEquals(c,restored);
        }
    }
    private static boolean escapes(BlockPos from,Set<BlockPos> solid){
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();queue.add(from);seen.add(from);
        while(!queue.isEmpty()){var p=queue.removeFirst();if(Math.abs(p.getX()-from.getX())>14 || Math.abs(p.getZ()-from.getZ())>14 || Math.abs(p.getY()-from.getY())>14)return true;
            for(var d:net.minecraft.core.Direction.values()){var q=p.relative(d);if(!solid.contains(q) && seen.add(q))queue.addLast(q);}}
        return false;
    }
}
