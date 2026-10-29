package cn.wwl.orimod.gametest;
import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.*;

/** Mirrors c's biome probes. Only the forest proposal uses the derived 80+16 search envelope. */
final class ForestReservationProbe {
    private final ServerLevel level;private final ChunkGeneratorStructureState placements;
    private final Map<String,Boolean> biomes=new HashMap<>();private long calls,queries;private final List<Map<String,Object>> hits=new ArrayList<>();
    private final ForestExternalTruth truth;private final boolean gladesRotationOnly;
    private final ForestCheapExternal cheap;
    private final List<Map<String,Object>> reviewed=new ArrayList<>();
    private static final List<ResourceLocation> SETS=List.of(ResourceLocation.withDefaultNamespace("villages"),ResourceLocation.withDefaultNamespace("pillager_outposts"),ResourceLocation.fromNamespaceAndPath("orimod","glades_outpost"));
    ForestReservationProbe(ServerLevel level){this(level,null,false);}
    ForestReservationProbe(ServerLevel level,ForestExternalTruth truth,boolean gladesRotationOnly){this(level,truth,gladesRotationOnly,null);}
    ForestReservationProbe(ServerLevel level,ForestCheapExternal cheap){this(level,null,false,cheap);}
    private ForestReservationProbe(ServerLevel level,ForestExternalTruth truth,boolean gladesRotationOnly,ForestCheapExternal cheap){this.level=level;this.truth=truth;this.gladesRotationOnly=gladesRotationOnly;this.cheap=cheap;placements=ChunkGeneratorStructureState.createForNormal(level.getChunkSource().randomState(),level.getSeed(),level.getChunkSource().getGenerator().getBiomeSource(),level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup());}
    String reject(Structure.GenerationContext c,RuinDecision site){calls++;
        var own=site.boxes().getFirst().mutable();for(var b:site.boxes())own.encapsulate(b.mutable());
        own=new BoundingBox(own.minX(),0,own.minZ(),own.maxX(),0,own.maxZ());
        for(var id:SETS){var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,id));
            boolean glades=id.getPath().equals("glades_outpost");int expansion=glades?48:96;
            for(int x=Math.floorDiv(own.minX()-expansion,16);x<=Math.floorDiv(own.maxX()+expansion,16);x++)for(int z=Math.floorDiv(own.minZ()-expansion,16);z<=Math.floorDiv(own.maxZ()+expansion,16);z++){
                if(!holder.value().placement().isStructureChunk(placements,x,z))continue;var cp=new ChunkPos(x,z);
                if(cheap!=null){if(!cheap.geometryPossible(holder,cp,site))continue;}
                else if(glades&&!own.intersects(outpost(cp)))continue;
                if(!biomes.computeIfAbsent(id+"@"+cp.toLong(),unused->possible(c,holder,cp)))continue;
                if(truth!=null){boolean rejected=truth.rejects(holder,cp,site,gladesRotationOnly);
                    reviewed.add(Map.of("forestChunk",List.of(c.chunkPos().x,c.chunkPos().z),"candidateStart",List.of(x,z),"set",id.toString(),"stillRejected",rejected));
                    if(!rejected)continue; // Keep searching: a false first hit cannot hide another real neighbour.
                }
                if(cheap!=null){boolean rejected=cheap.rejects(holder,cp,site);
                    reviewed.add(Map.of("forestChunk",List.of(c.chunkPos().x,c.chunkPos().z),"candidateStart",List.of(x,z),"set",id.toString(),"stillRejected",rejected));
                    if(!rejected)continue;
                }
                String reason="STRUCTURE_"+id.getPath().toUpperCase(Locale.ROOT);
                hits.add(Map.of("forestChunk",List.of(c.chunkPos().x,c.chunkPos().z),"candidateStart",List.of(x,z),"reason",reason));return reason;
            }
        }return null;
    }
    private boolean possible(Structure.GenerationContext c,Holder<StructureSet> set,ChunkPos cp){
        for(int dy:new int[]{0,-16,16,32,64}){queries++;var biome=c.biomeSource().getNoiseBiome(QuartPos.fromBlock(cp.getMiddleBlockX()),QuartPos.fromBlock(c.chunkGenerator().getSeaLevel()+dy),QuartPos.fromBlock(cp.getMiddleBlockZ()),c.randomState().sampler());
            if(set.value().structures().stream().anyMatch(e->e.structure().value().biomes().contains(biome)))return true;}
        return false;
    }
    private static BoundingBox outpost(ChunkPos cp){try{var cls=Class.forName("cn.wwl.orimod.world.CreekStructureReservations");var m=cls.getDeclaredMethod("outpostEnvelope",ChunkPos.class);m.setAccessible(true);return (BoundingBox)m.invoke(null,cp);}catch(Exception e){throw new IllegalStateException(e);}}
    Map<String,Object> report(){return Map.of("calls",calls,"biomeQueries",queries,"coarseHeightQueries",0,"hits",List.copyOf(hits),"reviewed",List.copyOf(reviewed),"searchBound","actual own XZ expanded 80+16 for vanilla jigsaw, existing exact glades envelope");}
    Map<String,Object> externalReport(){return cheap==null?Map.of():cheap.report();}
}
