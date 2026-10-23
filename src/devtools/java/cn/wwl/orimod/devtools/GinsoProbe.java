package cn.wwl.orimod.devtools;

import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.*;

/** Development-only noise survey, no chunk loads and no writes. Uses the real structure placement and preflight. */
final class GinsoProbe {
    static Map<String,Object> scan(ServerLevel level,BlockPos center,int radius){
        if(radius<1||radius>6)throw new IllegalArgumentException("Region radius must be 1..6");
        var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree"));
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree"));
        var placement=(RandomSpreadStructurePlacement)set.placement();
        var generator=level.getChunkSource().getGenerator();var randomState=level.getChunkSource().randomState();
        int sx=Math.floorDiv(center.getX()>>4,placement.spacing()),sz=Math.floorDiv(center.getZ()>>4,placement.spacing());
        var histogram=new TreeMap<String,Integer>();var sites=new ArrayList<Map<String,Object>>();
        long start=System.nanoTime();int before=level.getChunkSource().getLoadedChunksCount();
        for(int x=sx-radius;x<=sx+radius;x++)for(int z=sz-radius;z<=sz+radius;z++){
            var chunk=placement.getPotentialStructureChunk(level.getSeed(),x*placement.spacing(),z*placement.spacing());
            var c=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),randomState,level.getStructureManager(),level.getSeed(),chunk,level,structure.biomes()::contains);
            var check=GinsoStructure.preflight(c);histogram.merge(check.reason().name(),1,Integer::sum);
            if(check.reason()==GinsoSiteRules.Reason.OK){var p=check.origin();sites.add(Map.of("origin",List.of(p.getX(),p.getY(),p.getZ()),"biome",generator.getBiomeSource().getNoiseBiome(p.getX()>>2,p.getY()>>2,p.getZ()>>2,randomState.sampler()).unwrapKey().orElseThrow().location().toString()));}
        }
        return Map.of("seed",level.getSeed(),"histogram",histogram,"sites",sites,"spacing",placement.spacing(),"separation",placement.separation(),"elapsedMs",(System.nanoTime()-start)/1e6,"chunksBefore",before,"chunksAfter",level.getChunkSource().getLoadedChunksCount());
    }
    static Map<String,Object> nearby(ServerLevel level,BlockPos at){
        var found=new LinkedHashMap<BlockPos,GinsoPiece>();
        for(int x=(at.getX()>>4)-5;x<=(at.getX()>>4)+5;x++)for(int z=(at.getZ()>>4)-5;z<=(at.getZ()>>4)+5;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var start:chunk.getAllStarts().values())if(start.isValid()&&start.getStructure() instanceof GinsoStructure)
                for(var piece:start.getPieces())if(piece instanceof GinsoPiece g)found.put(g.origin(),g);
        }
        return Map.of("sites",found.values().stream().map(g->{
            var p=g.origin();var row=new LinkedHashMap<String,Object>();
            row.put("origin",xyz(p));row.put("height",GinsoPiece.shape(g.shapeSeed()).height);row.put("shapeSeed",g.shapeSeed());
            row.put("biome",level.getBiome(p).unwrapKey().orElseThrow().location().toString());row.put("baseBlock",level.getBlockState(p).toString());
            row.put("entranceTurns",g.entrance());row.put("approachOffset",g.approach());row.put("course",g.hasCourse());
            row.put("entry",xyz(g.point(new BlockPos(0,GinsoInterior.approachFloor(-21,g.approach())+1,-21))));
            row.put("heart",xyz(g.point(GinsoInterior.HEART)));row.put("checkpoints",GinsoInterior.WELLS.stream().map(g::point).map(GinsoProbe::xyz).toList());
            row.put("lantern",xyz(g.point(GinsoInterior.LANTERN)));row.put("flower",xyz(g.point(GinsoInterior.GRAPPLE)));row.put("vent",xyz(g.point(GinsoInterior.VENT)));
            row.put("sections",GinsoInterior.SECTIONS.stream().map(s->Map.of("id",s.id(),"start",xyz(g.point(s.start())),"goal",xyz(g.point(s.goal())),"floor",p.getY()+s.floor(),"top",p.getY()+s.top(),"required",s.required()==null?"basic":s.required().id())).toList());return row;
        }).toList());
    }
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
}
