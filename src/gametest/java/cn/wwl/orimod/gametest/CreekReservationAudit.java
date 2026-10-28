package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.*;
import java.util.*;

/** Counterfactual audit only. Never used by production placement. No terrain or chunk requests. */
final class CreekReservationAudit {
    static final int[] SEA_OFFSETS={0,-16,16,32,64};
    private ChunkGeneratorStructureState state;
    private final Map<String,Boolean> biomeCache=new HashMap<>();
    private final Map<Long,BoundingBox> outpostBoxes=new HashMap<>();
    private long queries,hits,checked;

    Map<String,Object> sample(Structure.GenerationContext c,RuinPlacement p,RuinKind.Layout layout){
        return sample(c,p,layout,List.of("a","b","c"));
    }
    Map<String,Object> sample(Structure.GenerationContext c,RuinPlacement p,RuinKind.Layout layout,List<String> variants){
        var biomeState=CreekAuditBiomeCache.capture(c.biomeSource());
        try {
        var registry=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        long setup=System.nanoTime();boolean cold=state==null;
        if(cold)state=ChunkGeneratorStructureState.createForNormal(c.randomState(),c.seed(),c.biomeSource(),registry.asLookup());
        double setupMs=(System.nanoTime()-setup)/1e6;
        var centre=new ChunkPos(p.block(new BlockPos(layout.size().getX()/2,0,layout.size().getZ()/2)));
        int range=(int)CreekRejectionAudit.callStatic("CreekStructureReservations","chunkRadius",new Class<?>[]{int.class,int.class},layout.size().getX(),layout.size().getZ());
        @SuppressWarnings("unchecked") var sets=(List<ResourceLocation>)CreekRejectionAudit.staticField("CreekStructureReservations","SETS");
        var own=p.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1));
        own=new BoundingBox(own.minX()-8,0,own.minZ()-8,own.maxX()+8,0,own.maxZ()+8);
        var entry=RuinApproachPiece.doorway(RuinKind.CREEK,layout,p).blocks(new BlockPos(-2,0,-9),new BlockPos(2,0,-1));
        own.encapsulate(new BoundingBox(entry.minX(),0,entry.minZ(),entry.maxX(),0,entry.maxZ()));
        var output=new LinkedHashMap<String,Object>();
        output.put("coldState",cold);output.put("stateSetupMs",setupMs);output.put("rotation",p.rotation().name());
        output.put("thread",Thread.currentThread().getName());output.put("heightQueries",0);output.put("mainThreadQueries",0);
        for(String variant:variants){
            long start=System.nanoTime();queries=hits=checked=0;
            var reasons=new ArrayList<String>();var details=new LinkedHashMap<String,Object>();
            for(var id:sets){
                var holder=registry.getHolder(ResourceKey.create(Registries.STRUCTURE_SET,id));if(holder.isEmpty())continue;
                long q0=queries,h0=hits,n0=checked,t0=System.nanoTime();
                var set=holder.get();var placement=set.value().placement();
                boolean exact=variant.equals("c")&&id.getPath().equals("glades_outpost");
                int minX=centre.x-range,maxX=centre.x+range,minZ=centre.z-range,maxZ=centre.z+range;
                // The maximum corner-anchored outpost/approach reach is < 48 blocks.
                // Search beyond the old radius too; exact geometry may add a missed hit.
                if(exact){minX=Math.floorDiv(own.minX()-48,16);maxX=Math.floorDiv(own.maxX()+48,16);minZ=Math.floorDiv(own.minZ()-48,16);maxZ=Math.floorDiv(own.maxZ()+48,16);}
                int potential=0,geometry=0,viable=0;var matched=new ArrayList<List<Integer>>();
                for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                    checked++;if(!placement.isStructureChunk(state,x,z))continue;
                    potential++;var cp=new ChunkPos(x,z);
                    if(exact&&!own.intersects(outpostEnvelope(cp)))continue;
                    geometry++;
                    if(!variant.equals("a")&&!biome(variant,id,set,c,cp))continue;
                    viable++;matched.add(List.of(x,z));
                }
                if(viable>0)reasons.add("STRUCTURE_"+id.getPath().toUpperCase(Locale.ROOT));
                details.put(id.toString(),Map.of("potential",potential,"geometry",geometry,"viable",viable,"hits",matched,
                        "biomeQueries",queries-q0,"cacheHits",hits-h0,"placementChecks",checked-n0,"ms",(System.nanoTime()-t0)/1e6));
                if(variant.equals("a")&&(viable>0)!=state.hasStructureChunkInRange(set,centre.x,centre.z,range))throw new IllegalStateException("Reservation enumeration drift");
            }
            output.put(variant,Map.of("reasons",reasons,"biomeQueries",queries,"cacheHits",hits,"placementChecks",checked,"ms",(System.nanoTime()-start)/1e6,"sets",details));
        }
        return output;
        } finally { biomeState.restore(); }
    }
    private boolean biome(String variant,ResourceLocation id,Holder<StructureSet> set,Structure.GenerationContext c,ChunkPos cp){
        String key=variant+":"+id+":"+cp.toLong();var cached=biomeCache.get(key);if(cached!=null){hits++;return cached;}
        boolean valid=false;int sea=c.chunkGenerator().getSeaLevel();
        for(int offset:SEA_OFFSETS){
            queries++;var biome=c.biomeSource().getNoiseBiome(QuartPos.fromBlock(cp.getMiddleBlockX()),QuartPos.fromBlock(sea+offset),QuartPos.fromBlock(cp.getMiddleBlockZ()),c.randomState().sampler());
            if(set.value().structures().stream().anyMatch(e->e.structure().value().biomes().contains(biome))){valid=true;break;}
        }
        biomeCache.put(key,valid);return valid;
    }
    private BoundingBox outpostEnvelope(ChunkPos cp){return outpostBoxes.computeIfAbsent(cp.toLong(),unused->{
        BoundingBox result=null;
        for(var r:Rotation.values()){
            var p=GladesOutpostStructure.placement(cp.getWorldPosition(),r);
            var b=p.blocks(BlockPos.ZERO,GladesOutpostPiece.SIZE.offset(-1,-1,-1));
            b=new BoundingBox(b.minX(),0,b.minZ(),b.maxX(),0,b.maxZ());
            var approach=new RuinPlacement(p.block(GladesOutpostPiece.ENTRY),r).blocks(new BlockPos(-2,0,-9),new BlockPos(2,0,-1));
            b.encapsulate(new BoundingBox(approach.minX(),0,approach.minZ(),approach.maxX(),0,approach.maxZ()));
            if(result==null)result=b;else result.encapsulate(b);
        }return result;
    });}
}
