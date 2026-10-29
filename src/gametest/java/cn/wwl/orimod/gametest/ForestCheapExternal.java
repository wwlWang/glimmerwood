package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only cheap precision proposal. Only findValidGenerationPoint; never invokes the returned builder. */
public final class ForestCheapExternal {
    private record Key(ResourceLocation set,long chunk) {}
    record Attempt(String structure,boolean valid,BlockPos stub,List<String> biomeTests,List<Long> queries) {
        Attempt { biomeTests=List.copyOf(biomeTests);queries=List.copyOf(queries); }
    }
    record Decision(boolean valid,String selected,List<RuinDecision.Box> boxes,List<Attempt> attempts) {
        Decision { boxes=List.copyOf(boxes);attempts=List.copyOf(attempts); }
    }
    private final ServerLevel level;
    private final LevelHeightAccessor height;
    // Same bounded, single-flight implementation as production State. No global lifetime/cache keys.
    private final RuinAuditCache<Key,Decision> cache=new RuinAuditCache<>(8192);
    private final ConcurrentLinkedQueue<Map<String,Object>> calls=new ConcurrentLinkedQueue<>(),computations=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String,Object>> geometry=new ConcurrentLinkedQueue<>();
    private final AtomicInteger factories=new AtomicInteger();
    private static final ThreadLocal<long[]> ACTIVE=new ThreadLocal<>();
    public static void count(int kind) {
        var counts=ACTIVE.get();if(counts==null)return;counts[kind]++;
        if(kind>=3)throw new AssertionError("Cheap external query crossed boundary: "+kind);
    }
    ForestCheapExternal(ServerLevel level){this.level=level;height=LevelHeightAccessor.create(level.getMinBuildHeight(),level.getHeight());}
    private Structure.GenerationContext context(Structure structure,ChunkPos cp,List<String> biomes){
        var g=level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(),g,g.getBiomeSource(),level.getChunkSource().randomState(),
                level.getStructureManager(),level.getSeed(),cp,height,b->{boolean allowed=structure.biomes().contains(b);
                    biomes.add(b.unwrapKey().orElseThrow().location()+"="+allowed);return allowed;});
    }
    Decision lookup(Holder<StructureSet> set,ChunkPos cp){
        var id=set.unwrapKey().orElseThrow().location();boolean[] computed={false};long start=System.nanoTime();
        var result=cache.get(new Key(id,cp.toLong()),()->{computed[0]=true;return compute(set,cp);});
        double ms=(System.nanoTime()-start)/1e6;
        calls.add(Map.of("set",id.toString(),"chunk",List.of(cp.x,cp.z),"ms",ms,"valid",result.valid,
                "computed",computed[0]));return result;
    }
    /** Runs before even the coarse biome probe. Only coordinates and the context's first random draw. */
    boolean geometryPossible(Holder<StructureSet> set,ChunkPos cp,RuinDecision own){
        var id=set.unwrapKey().orElseThrow().location();boolean glades=id.getPath().equals("glades_outpost");
        var boxes=new ArrayList<RuinDecision.Box>();long[] queries=new long[5];
        if(ACTIVE.get()!=null)throw new AssertionError("Recursive geometry probe");
        ACTIVE.set(queries);
        boolean intersects=false;String rotation="NONE";
        try{
            if(glades){
                var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
                var r=Rotation.getRandom(random);rotation=r.name();
                var at=GladesOutpostStructure.placement(cp.getWorldPosition(),r);
                boxes.add(RuinDecision.Box.of(at.blocks(BlockPos.ZERO,GladesOutpostPiece.SIZE.offset(-1,-1,-1))));
                var door=new RuinPlacement(at.block(GladesOutpostPiece.ENTRY),r);
                boxes.add(RuinDecision.Box.of(door.blocks(new BlockPos(-2,-24,-9),new BlockPos(2,64,-1))));
                for(var a:own.boxes())for(var b:boxes)intersects|=a.intersects(b,true);
            }else{
                // Preserve the conservative 96-block search against the complete own envelope.
                var envelope=own.boxes().getFirst().mutable();for(var b:own.boxes())envelope.encapsulate(b.mutable());
                intersects=cp.getMaxBlockX()>=envelope.minX()-96&&cp.getMinBlockX()<=envelope.maxX()+96
                        &&cp.getMaxBlockZ()>=envelope.minZ()-96&&cp.getMinBlockZ()<=envelope.maxZ()+96;
            }
        }finally{ACTIVE.remove();}
        if(Arrays.stream(queries).sum()!=0)throw new AssertionError("Geometry queried terrain");
        geometry.add(Map.of("set",id.toString(),"chunk",List.of(cp.x,cp.z),"ownOrigin",List.of(own.placement().origin().getX(),own.placement().origin().getZ()),
                "rotation",rotation,"possible",intersects,"queries",Arrays.stream(queries).boxed().toList()));
        return intersects;
    }
    private Decision compute(Holder<StructureSet> set,ChunkPos cp){
        factories.incrementAndGet();long begin=System.nanoTime();
        var entries=new ArrayList<>(set.value().structures());var random=new WorldgenRandom(new LegacyRandomSource(0));
        random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        var attempts=new ArrayList<Attempt>();Decision decision=new Decision(false,"NONE",List.of(),List.of());
        while(!entries.isEmpty()){
            int i=0;if(entries.size()>1){int roll=random.nextInt(total);for(var e:entries){roll-=e.weight();if(roll<0)break;i++;}}
            var entry=entries.remove(i);total-=entry.weight();var structure=entry.structure().value();var biomes=new ArrayList<String>();
            long[] queries=new long[5];Optional<Structure.GenerationStub> stub;
            if(ACTIVE.get()!=null)throw new AssertionError("Unexpected recursive external query");
            ACTIVE.set(queries);
            try{stub=structure.findValidGenerationPoint(context(structure,cp,biomes));}
            finally{ACTIVE.remove();}
            if(!(structure instanceof GladesOutpostStructure)&&(queries[0]>1||queries[1]!=0||queries[2]!=0))
                throw new AssertionError("Vanilla qualification expanded beyond one height query "+Arrays.toString(queries));
            attempts.add(new Attempt(entry.structure().unwrapKey().orElseThrow().location().toString(),stub.isPresent(),
                    stub.map(Structure.GenerationStub::position).orElse(null),biomes,Arrays.stream(queries).boxed().toList()));
            if(stub.isEmpty())continue;
            var boxes=new ArrayList<RuinDecision.Box>();
            if(structure instanceof GladesOutpostStructure){
                var rotation=Rotation.getRandom(context(structure,cp,new ArrayList<>()).random());
                var local=new RuinPlacement(BlockPos.ZERO,rotation).block(GladesOutpostPiece.ENTRY);
                var at=new RuinPlacement(stub.get().position().subtract(local),rotation);
                boxes.add(RuinDecision.Box.of(at.blocks(BlockPos.ZERO,GladesOutpostPiece.SIZE.offset(-1,-1,-1))));
                var door=new RuinPlacement(stub.get().position(),rotation);
                boxes.add(RuinDecision.Box.of(door.blocks(new BlockPos(-2,-24,-9),new BlockPos(2,64,-1))));
            }
            decision=new Decision(true,entry.structure().unwrapKey().orElseThrow().location().toString(),boxes,attempts);break;
        }
        if(!decision.valid)decision=new Decision(false,"NONE",List.of(),attempts);
        double ms=(System.nanoTime()-begin)/1e6;
        computations.add(Map.of("set",set.unwrapKey().orElseThrow().location().toString(),"chunk",List.of(cp.x,cp.z),"ms",ms,"decision",decision));
        return decision;
    }
    boolean rejects(Holder<StructureSet> set,ChunkPos cp,RuinDecision own){
        var d=lookup(set,cp);if(!d.valid)return false;
        if(!set.unwrapKey().orElseThrow().location().getPath().equals("glades_outpost"))return true;
        for(var a:own.boxes())for(var b:d.boxes)if(a.intersects(b,false))return true;
        return false;
    }
    Map<String,Object> report(){return Map.of("calls",List.copyOf(calls),"computations",List.copyOf(computations),"cache",cache.stats(),"factories",factories.get(),"geometry",List.copyOf(geometry),
            "queryColumns",List.of("getBaseHeight","getBaseColumn","noiseCells","chunkRequests","structureBuilders"),
            "lifetime","one server/registry audit instance; bounded single-flight cache; immutable decisions; no static result map");}
    /** Separate post-grid hit benchmark: never prewarms or changes the measured grid's decisions. */
    Map<String,Object> warmCacheProbe(){
        int beforeCalls=calls.size(),beforeFactories=factories.get();var keys=List.copyOf(computations);
        for(var row:keys){
            var id=ResourceLocation.parse((String)row.get("set"));var xy=(List<?>)row.get("chunk");
            var cp=new ChunkPos((Integer)xy.get(0),(Integer)xy.get(1));
            var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                    .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET,id));
            for(int i=0;i<32;i++)lookup(holder,cp);
        }
        if(factories.get()!=beforeFactories)throw new AssertionError("Warm probe recomputed qualification");
        return Map.of("calls",List.copyOf(calls).subList(beforeCalls,calls.size()),"newComputations",factories.get()-beforeFactories,
                "scope","32 completed-cache hits per computed key, AFTER full grid/reference/pair audit; excluded from grid timing");
    }
    static Map<String,Object> concurrency(ServerLevel level,Holder<StructureSet> holder,ChunkPos cp,Decision expected)throws Exception{
        var fresh=new ForestCheapExternal(level);var executor=java.util.concurrent.Executors.newFixedThreadPool(4);
        try{
            var tasks=new ArrayList<java.util.concurrent.Callable<Decision>>();for(int i=0;i<16;i++)tasks.add(()->fresh.lookup(holder,cp));
            for(var f:executor.invokeAll(tasks))if(!expected.equals(f.get()))throw new AssertionError("Concurrent immutable decision drift");
            if(fresh.factories.get()!=1)throw new AssertionError("Single flight failed "+fresh.factories);
            return Map.of("queries",16,"workers",4,"computations",fresh.factories.get(),"equal",true);
        }finally{executor.shutdown();}
    }
}
