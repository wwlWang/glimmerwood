package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.RuinKind;
import cn.wwl.orimod.world.RuinPiece;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Dedicated normal-world measurement. No fake GameTest terrain, no client renderer or forced tickets. */
final class CreekProfileAudit {
    static void started(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        boolean seams="creek_seams".equals(System.getProperty("orimod.worldgen.audit.mode"));
        var report=new LinkedHashMap<String,Object>();var rows=new ArrayList<Map<String,Object>>();
        var commands=new ArrayList<Map<String,Object>>();report.put("locateCommands",commands);
        boolean deepProfile=Boolean.getBoolean("orimod.worldgen.audit.deepProfile");
        report.put("deepProfile",deepProfile);
        report.put("seed",level.getSeed());report.put("profileRuins",Boolean.getBoolean("orimod.profileRuins"));
        report.put("sites",rows);
        try(var recording=new Recording()) {
            recording.enable("minecraft.ChunkGeneration").withThreshold(Duration.ZERO).withoutStackTrace();
            recording.enable("jdk.GarbageCollection");
            recording.enable("jdk.GCPhasePause").withThreshold(Duration.ZERO);
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1));
            // High-frequency stack/allocation sampling is a separate diagnosis,
            // not part of the lightweight write-time acceptance measurement.
            if(deepProfile){
                recording.enable("jdk.ObjectAllocationSample");
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            }
            recording.enable("orimod.CreekTerrainSampling").withThreshold(Duration.ZERO);
            recording.enable("orimod.CreekTemplate").withThreshold(Duration.ZERO);
            recording.start();
            for(var kind:seams?new RuinKind[]{RuinKind.CREEK}:new RuinKind[]{RuinKind.CREEK,RuinKind.FOREST,RuinKind.CAVE}){
                var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                        .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,kind.location()));
                var from=kind==RuinKind.CREEK?level.getSharedSpawnPos():new BlockPos(0,110,128);
                var locateStart=Instant.now();long locateNanos=System.nanoTime();
                var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),from,100,false);
                double locateMs=(System.nanoTime()-locateNanos)/1e6;var locateEnd=Instant.now();
                if(found==null)throw new IllegalStateException("Missing natural "+kind.id);
                var cp=new ChunkPos(found.getFirst());
                var start=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
                var piece=start.getPieces().stream().filter(RuinPiece.class::isInstance).map(RuinPiece.class::cast).findFirst().orElseThrow();
                var box=piece.getBoundingBox();var chunks=new ArrayList<Map<String,Object>>();
                var row=new LinkedHashMap<String,Object>();row.put("kind",kind.id);row.put("origin",java.util.List.of(piece.origin().getX(),piece.origin().getY(),piece.origin().getZ()));
                row.put("rotation",piece.getRotation().name());row.put("chunks",chunks);rows.add(row);
                row.put("locateMs",locateMs);row.put("locateStart",locateStart.toString());row.put("locateEnd",locateEnd.toString());
                var queryStart=Instant.now();
                holder.value().findValidGenerationPoint(new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                        level.registryAccess(),level.getChunkSource().getGenerator(),level.getChunkSource().getGenerator().getBiomeSource(),
                        level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),cp,level,holder.value().biomes()::contains));
                row.put("viabilityStart",queryStart.toString());row.put("viabilityEnd",Instant.now().toString());
                var repeatStart=Instant.now();long repeatNanos=System.nanoTime();
                var again=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),from,100,false);
                if(!found.equals(again))throw new IllegalStateException("Repeated locate changed site");
                row.put("repeatMs",(System.nanoTime()-repeatNanos)/1e6);row.put("repeatStart",repeatStart.toString());row.put("repeatEnd",Instant.now().toString());
                // Centre outward, as for a player arriving at the site. One neighbour ring
                // settles the natural footprint. getChunk waits for real worldgen workers.
                var positions=new ArrayList<ChunkPos>();
                for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++)
                    for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++)positions.add(new ChunkPos(x,z));
                positions.sort(java.util.Comparator.comparingInt(p->Math.max(Math.abs(p.x-cp.x),Math.abs(p.z-cp.z))));
                for(var p:positions){
                    long before=System.nanoTime();var chunk=level.getChunk(p.x,p.z,ChunkStatus.FULL,true);
                    double ms=(System.nanoTime()-before)/1e6;
                    chunks.add(Map.of("x",p.x,"z",p.z,"fullRequestMs",ms,"blockEntities",chunk.getBlockEntitiesPos().size()));
                }
                if(kind==RuinKind.CREEK){
                    row.put("naturalTerrain",CreekTerrainGameTests.naturalScan(level,piece));
                    var witnesses=new ArrayList<Map<String,Object>>();row.put("seamWitnesses",witnesses);
                    for(int[] local:new int[][]{{49,15},{50,15},{49,16},{50,16},{48,8},{56,8},{48,16},{56,16},{48,24},{56,24}}){
                        var at=piece.placement().block(new BlockPos(local[0],0,local[1]));
                        int pure=level.getChunkSource().getGenerator().getBaseHeight(at.getX(),at.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState())-1;
                        witnesses.add(Map.of("local",java.util.List.of(local[0],local[1]),"world",java.util.List.of(at.getX(),at.getZ()),"pureGround",pure,"relativePure",pure-piece.origin().getY()));
                    }
                }
            }
            // Spawn-area cave starts can already be known. Use a second,
            // unexplored common origin for genuine end-to-end command timings.
            for(var kind:seams?new RuinKind[0]:new RuinKind[]{RuinKind.CREEK,RuinKind.FOREST,RuinKind.CAVE}){
                var source=server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(8000,110,8000));
                var cmd="locate structure "+kind.location();var row=new LinkedHashMap<String,Object>();row.put("kind",kind.id);row.put("command",cmd);
                for(var phase:new String[]{"first","repeat"}){
                    row.put(phase+"Start",Instant.now().toString());long before=System.nanoTime();
                    server.getCommands().performPrefixedCommand(source,cmd);
                    row.put(phase+"Ms",(System.nanoTime()-before)/1e6);row.put(phase+"End",Instant.now().toString());
                }
                commands.add(row);
            }
            recording.stop();var file=Path.of("creek-natural.jfr");recording.dump(ReportFiles.output(file));
            var events=new ArrayList<Map<String,Object>>();var samplings=new ArrayList<Map<String,Object>>();
            try(var reader=new RecordingFile(file)){
                while(reader.hasMoreEvents()){
                    var e=reader.readEvent();
                    if(e.getEventType().getName().equals("orimod.CreekTerrainSampling")){
                        var when=e.getStartTime();var thread=e.getThread().getJavaName();
                        if(thread.equals(Thread.currentThread().getName()))throw new IllegalStateException("New terrain sampling on main thread");
                        for(var r:rows)for(var phase:new String[]{"viability","repeat"}){
                            if(!when.isBefore(Instant.parse((String)r.get(phase+"Start")))&&when.isBefore(Instant.parse((String)r.get(phase+"End"))))
                                throw new IllegalStateException("Unexpected sampling during "+phase+" "+r.get("kind"));
                        }
                        for(var r:commands)if(!when.isBefore(Instant.parse((String)r.get("repeatStart")))&&when.isBefore(Instant.parse((String)r.get("repeatEnd"))))
                            throw new IllegalStateException("Repeated command rebuilt terrain "+r.get("kind"));
                        samplings.add(Map.of("start",when.toString(),"ms",e.getDuration().toNanos()/1e6,"cpuMs",e.getLong("cpuNanos")/1e6,
                                "calls",e.getInt("calls"),"reused",e.getInt("reused"),"gridCorners",e.getInt("gridCorners"),"step",e.getInt("gridStep"),"thread",thread,"cause",e.getString("cause"),
                                "origin",java.util.List.of(e.getInt("originX"),e.getInt("originZ"))));
                    }
                    if(e.getEventType().getName().equals("minecraft.ChunkGeneration"))events.add(Map.of(
                            "x",e.getInt("chunkPosX"),"z",e.getInt("chunkPosZ"),"status",e.getString("status"),
                            "ms",e.getDuration().toNanos()/1e6,"thread",e.getThread().getJavaName()));
                }
            }
            if(!seams&&samplings.isEmpty())throw new IllegalStateException("Sampling recorder did not observe any fresh start");
            report.put("terrainSamplingEvents",samplings);report.put("newSamplesMain",0);report.put("newSamplesViability",0);report.put("newSamplesRepeatLocate",0);
            report.put("chunkGenerationEvents",events);report.put("status","complete");
        }catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{
            try{Files.writeString(ReportFiles.output(Path.of("creek-profile.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(Exception e){throw new IllegalStateException(e);}
            finally{
                // This headless harness owns synchronous chunk requests too.
                // Finish their generation continuations outside all measurement
                // intervals before asking the ordinary server to shut down.
                try{CreekFixtureChunks.settle(level,"audit-before-stop");}
                finally{server.halt(false);}
            }
        }
    }
    private CreekProfileAudit(){}
}
