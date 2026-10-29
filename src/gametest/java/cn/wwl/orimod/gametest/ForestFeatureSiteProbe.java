package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.*;
import java.util.*;

/** Diagnostic only: existing priority, no reservation proposal or production decision mutation. */
final class ForestFeatureSiteProbe {
    private static List<BlockPos> candidates(BlockPos origin){
        var out=new ArrayList<BlockPos>();out.add(origin);var q=new ArrayList<BlockPos>();
        for(int x:new int[]{4,12})for(int z:new int[]{4,12}){var p=new BlockPos((origin.getX()&~15)+x,origin.getY(),(origin.getZ()&~15)+z);if(p.distManhattan(origin)>3)q.add(p);}
        q.sort(Comparator.comparingInt(p->p.distManhattan(origin)));out.addAll(q.subList(0,Math.min(2,q.size())));return out;
    }
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private static boolean blocked(ServerLevel level,BlockPos p,int margin,JsonArray rows){
        for(var r:rows){var row=r.getAsJsonObject();if(!row.has("boxes"))continue;
            var cp=row.getAsJsonArray("chunk");if(Math.abs(cp.get(0).getAsInt()-(p.getX()>>4))>4||Math.abs(cp.get(1).getAsInt()-(p.getZ()>>4))>4)continue;
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(var bv:row.getAsJsonArray("boxes")){var b=bv.getAsJsonArray();minX=Math.min(minX,b.get(0).getAsInt());minZ=Math.min(minZ,b.get(2).getAsInt());maxX=Math.max(maxX,b.get(3).getAsInt());maxZ=Math.max(maxZ,b.get(5).getAsInt());}
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("orimod",row.get("kind").getAsString()));
            // blocksTree reads StructureStart.getBoundingBox, including vanilla terrain adaptation padding.
            var adjusted=structure.adjustBoundingBox(new BoundingBox(minX,0,minZ,maxX,0,maxZ));
            if(p.getX()>=adjusted.minX()-margin&&p.getX()<=adjusted.maxX()+margin&&p.getZ()>=adjusted.minZ()-margin&&p.getZ()<=adjusted.maxZ()+margin)return true;
        }return false;
    }
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"feature-ghost-diagnostic-worker"));
        try{executor.submit(()->{try{
            var a=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.featureGhostA")))).getAsJsonObject().getAsJsonObject("baseline").getAsJsonArray("rows");
            var b=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.featureGhostB")))).getAsJsonObject().getAsJsonArray("rows");
            if(a.size()!=2025||b.size()!=2025)throw new IllegalArgumentException("2025 grid required");
            var level=server.overworld();report.put("seed",level.getSeed());
            // The exact 45x45 random-spread regions, not the irregular min/max of their selected chunks.
            int minRegionX=999,minRegionZ=999,maxRegionX=-999,maxRegionZ=-999;
            for(var v:b){var c=v.getAsJsonObject().getAsJsonArray("chunk");int x=Math.floorDiv(c.get(0).getAsInt(),6),z=Math.floorDiv(c.get(1).getAsInt(),6);minRegionX=Math.min(minRegionX,x);maxRegionX=Math.max(maxRegionX,x);minRegionZ=Math.min(minRegionZ,z);maxRegionZ=Math.max(maxRegionZ,z);}
            int minX=minRegionX*6,minZ=minRegionZ*6,maxX=maxRegionX*6+5,maxZ=maxRegionZ*6+5;
            report.put("chunkDomain",List.of(minX,minZ,maxX,maxZ));report.put("ruinGridPoints",2025);
            report.put("scope","Noise-qualified FeatureSite starts in the same grid domain; RUIN ghosts caused by these 2025 selected ruin starts. Three retry positions, vanilla Structure.adjustBoundingBox and START_RADIUS=4 reproduced; other terrain/clearance phantoms and unchanged outside-domain/glades/ginso guards are not counted. This is a guard prediction, not 2025 worlds with actual feature execution.");
            var placements=ChunkGeneratorStructureState.createForNormal(level.getChunkSource().randomState(),level.getSeed(),level.getChunkSource().getGenerator().getBiomeSource(),level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup());
            var rows=new ArrayList<Map<String,Object>>();var totals=new LinkedHashMap<String,Object>();report.put("rows",rows);report.put("totals",totals);
            for(String name:List.of("spirit_tree","blighted_grounds")){
                var id=ResourceLocation.fromNamespaceAndPath("orimod",name);var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,id));
                int potential=0,valid=0,oldGhost=0,newGhost=0,added=0,removed=0;long t=System.nanoTime();
                for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                    if(!set.value().placement().isStructureChunk(placements,x,z))continue;potential++;
                    var structure=set.value().structures().getFirst().structure().value();var generator=level.getChunkSource().getGenerator();var cp=new ChunkPos(x,z);
                    var context=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),cp,LevelHeightAccessor.create(level.getMinBuildHeight(),level.getHeight()),structure.biomes()::contains);
                    var stub=structure.findValidGenerationPoint(context);if(stub.isEmpty())continue;valid++;var origin=stub.get().position();int margin=name.equals("spirit_tree")?SpiritTreeShape.RADIUS:12;
                    var tries=candidates(origin);boolean ga=tries.stream().allMatch(p->blocked(level,p,margin,a)),gb=tries.stream().allMatch(p->blocked(level,p,margin,b));
                    if(ga)oldGhost++;if(gb)newGhost++;if(!ga&&gb)added++;if(ga&&!gb)removed++;
                    rows.add(Map.of("site",name,"chunk",List.of(x,z),"origin",xyz(origin),"candidates",tries.stream().map(ForestFeatureSiteProbe::xyz).toList(),"publishedRuinYield",ga,"newForestRuinYield",gb));
                }
                totals.put(name,Map.of("potential",potential,"noiseQualified",valid,"publishedRuinYield",oldGhost,"newForestRuinYield",newGhost,"added",added,"removed",removed,"ms",(System.nanoTime()-t)/1e6));
            }
            report.put("status","complete");
        }catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();try{Files.writeString(Path.of("forest-feature-ghosts.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}finally{server.halt(false);}}
    }
}
