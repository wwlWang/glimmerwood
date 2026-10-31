package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.*;
import java.util.*;

/** Diagnose lost successful baseline sites without generating their chunks or touching frozen resources. */
final class P5FeaturePreflightAudit {
    static void started(ServerStartedEvent e){
        var level=e.getServer().overworld();var results=new ArrayList<Map<String,Object>>();
        try{
            var baseline=JsonParser.parseString(Files.readString(Path.of(System.getProperty("p5.baseline")))).getAsJsonObject();
            var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
            var terrainType=Class.forName("cn.wwl.orimod.world.FeatureTerrain");
            var terrainCtor=Class.forName("cn.wwl.orimod.world.FeatureTerrain$Noise").getDeclaredConstructor(ChunkGenerator.class,RandomState.class,net.minecraft.world.level.LevelHeightAccessor.class,BlockPos.class);terrainCtor.setAccessible(true);
            var check=Class.forName("cn.wwl.orimod.world.FeatureSitePlan").getDeclaredMethod("check",FeatureSiteStructure.Site.class,terrainType,BlockPos.class,long.class);check.setAccessible(true);
            var candidates=FeatureSitePiece.class.getDeclaredMethod("candidates",BlockPos.class);candidates.setAccessible(true);
            var reservations=FeatureSiteStructure.class.getDeclaredField("reservations");reservations.setAccessible(true);
            for(var value:baseline.getAsJsonArray("rows")){
                var row=value.getAsJsonObject();if(!row.getAsJsonObject("outcome").get("placed").getAsBoolean())continue;
                String kind=row.get("kind").getAsString();var cp=new ChunkPos(row.getAsJsonArray("chunk").get(0).getAsInt(),row.getAsJsonArray("chunk").get(1).getAsInt());
                var structure=(FeatureSiteStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("orimod",kind));
                var c=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),random,level.getStructureManager(),level.getSeed(),cp,level,structure.biomes()::contains);
                var origin=new BlockPos(cp.getMinBlockX()+c.random().nextInt(16),0,cp.getMinBlockZ()+c.random().nextInt(16));long seed=c.random().nextLong();
                var terrain=terrainCtor.newInstance(generator,random,level,origin);var reserve=reservations.get(structure);
                var reject=reserve.getClass().getDeclaredMethod("rejection",Structure.GenerationContext.class,Class.forName("cn.wwl.orimod.world.FeatureSitePlan"),FeatureSiteStructure.Site.class);reject.setAccessible(true);
                var atList=new ArrayList<Map<String,Object>>();
                for(var p:(List<BlockPos>)candidates.invoke(null,origin)){
                    var plan=check.invoke(null,structure.site,terrain,p,seed);var reason=plan.getClass().getDeclaredMethod("reason");reason.setAccessible(true);
                    var at=new LinkedHashMap<String,Object>();at.put("at",p.toShortString());at.put("reservation",reject.invoke(reserve,c,plan,structure.site));at.put("terrain",reason.invoke(plan));atList.add(at);
                }
                var out=new LinkedHashMap<String,Object>();out.put("kind",kind);out.put("oldOrigin",row.get("origin").getAsString());out.put("oldPlacedAt",row.getAsJsonObject("outcome").get("at").getAsString());out.put("candidates",atList);results.add(out);
                System.out.println("P5_LOST_SITE "+out);
                Files.writeString(Path.of("p5-preflight-diagnosis.json"),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(results));
            }
        }catch(Exception ex){throw new IllegalStateException(ex);}finally{e.getServer().halt(false);}
    }
}
