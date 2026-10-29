package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForestStructureReservationsTest {
    private record Entry(String id,int weight) {}
    private static final List<Entry> ENTRIES=List.of(new Entry("plains",1),new Entry("desert",2),
            new Entry("savanna",1),new Entry("snowy",3),new Entry("taiga",1));
    // Independent vanilla remove-on-failure reference, including the last remaining draw.
    private static List<Entry> vanillaOrder(long seed,ChunkPos cp){
        var left=new ArrayList<>(ENTRIES);var result=new ArrayList<Entry>();
        var r=new WorldgenRandom(new LegacyRandomSource(0));r.setLargeFeatureSeed(seed,cp.x,cp.z);
        while(!left.isEmpty()){
            int ticket=r.nextInt(left.stream().mapToInt(Entry::weight).sum()),index=0;
            while(ticket>=left.get(index).weight()){ticket-=left.get(index).weight();index++;}
            result.add(left.remove(index));
        }
        return result;
    }
    @Test void firstFailureFallsBackWithVanillaSeedAndRemovalOrder(){
        for(long seed:new long[]{0,116118,116120})for(var cp:List.of(new ChunkPos(-118,-112),new ChunkPos(-89,-47),new ChunkPos(78,79))){
            var expected=vanillaOrder(seed,cp);var attempted=new ArrayList<Entry>();
            var chosen=ForestStructureReservations.select(ENTRIES,Entry::weight,seed,cp,e->{
                attempted.add(e);return attempted.size()==2?Optional.of(e):Optional.empty();});
            assertEquals(expected.subList(0,2),attempted);
            assertEquals(Optional.of(expected.get(1)),chosen);
            assertEquals(5,ENTRIES.size(),"Do not mutate registry-owned entries");
        }
    }
    @Test void everyFallbackDepthMatchesVanillaIncludingAllFiveFailures(){
        var cp=new ChunkPos(-118,-112);var expected=vanillaOrder(116118,cp);
        for(int winner=0;winner<=5;winner++){
            int target=winner;var attempts=new ArrayList<Entry>();
            var result=ForestStructureReservations.select(ENTRIES,Entry::weight,116118,cp,e->{
                attempts.add(e);return expected.indexOf(e)==target?Optional.of(e):Optional.empty();});
            assertEquals(expected.subList(0,Math.min(5,winner+1)),attempts);
            assertEquals(winner<5?Optional.of(expected.get(winner)):Optional.empty(),result);
        }
    }
    @Test void singletonAttemptsOnceAndEmptySetDoesNothing(){
        var attempts=new ArrayList<Entry>();var e=ENTRIES.getFirst();
        assertEquals(Optional.of(e),ForestStructureReservations.select(List.of(e),Entry::weight,1,new ChunkPos(3,4),x->{attempts.add(x);return Optional.of(x);}));
        assertEquals(List.of(e),attempts);
        assertEquals(Optional.empty(),ForestStructureReservations.select(List.<Entry>of(),Entry::weight,1,new ChunkPos(3,4),x->{fail("Empty set attempted");return Optional.of(x);}));
    }
    @Test void qualificationCopiesMutableInputs(){
        var p=new BlockPos.MutableBlockPos(3,70,4);var boxes=new ArrayList<RuinDecision.Box>();
        boxes.add(new RuinDecision.Box(0,1,2,3,4,5));
        var q=new ForestStructureReservations.Qualification(ResourceLocation.parse("orimod:glades_outpost"),p,boxes);
        p.set(9,9,9);boxes.clear();assertEquals(new BlockPos(3,70,4),q.entry());assertEquals(1,q.boxes().size());
        assertThrows(UnsupportedOperationException.class,()->q.boxes().clear());
    }
}
