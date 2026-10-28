package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.RuinKind;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Same seed, same unvisited command origin; also usable against the archived baseline. */
final class CreekLocateAudit {
    static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();var output=new LinkedHashMap<String,Object>();
        var commands=new ArrayList<Map<String,Object>>();output.put("commands",commands);output.put("seed",level.getSeed());
        var kinds=new RuinKind[]{RuinKind.CREEK,RuinKind.FOREST,RuinKind.CAVE};
        try(var recording=new Recording()){
            // Match the earlier profile's spawn-area warm queries. The measured
            // commands below always use a fresh remote origin, never the warm site.
            var warm=new ArrayList<Map<String,Object>>();output.put("warmQueries",warm);
            for(var kind:kinds){
                var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,kind.location()));
                long before=System.nanoTime();
                var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),kind==RuinKind.CREEK?level.getSharedSpawnPos():new BlockPos(0,110,128),100,false);
                warm.add(Map.of("kind",kind.id,"ms",(System.nanoTime()-before)/1e6,"position",String.valueOf(found)));
            }
            CreekFixtureChunks.settle(level,"locate-warm");
            for(var name:new String[]{"orimod.RuinSiting","orimod.LocateCandidate","orimod.CreekTerrainSampling"})recording.enable(name).withThreshold(Duration.ZERO);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));recording.start();
            for(var kind:kinds){
                var source=server.createCommandSourceStack().withPosition(new Vec3(8000,110,8000));
                var row=new LinkedHashMap<String,Object>();row.put("kind",kind.id);commands.add(row);
                for(var phase:new String[]{"first","repeat"}){
                    row.put(phase+"Start",Instant.now().toString());long before=System.nanoTime();
                    server.getCommands().performPrefixedCommand(source,"locate structure "+kind.location());
                    row.put(phase+"Ms",(System.nanoTime()-before)/1e6);row.put(phase+"End",Instant.now().toString());
                }
                var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,kind.location()));
                var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),new BlockPos(8000,110,8000),100,false);
                if(found==null)throw new IllegalStateException("locate returned no site");
                var p=found.getFirst();row.put("position",List.of(p.getX(),p.getY(),p.getZ()));row.put("distance",Math.hypot(p.getX()-8000,p.getZ()-8000));
            }
            recording.stop();recording.dump(Path.of("locate.jfr"));
            var events=new ArrayList<Map<String,Object>>();output.put("events",events);
            try(var reader=new RecordingFile(Path.of("locate.jfr"))){while(reader.hasMoreEvents()){
                var e=reader.readEvent();var name=e.getEventType().getName();
                if(!name.startsWith("orimod."))continue;
                var r=new LinkedHashMap<String,Object>();r.put("type",name);r.put("start",e.getStartTime().toString());r.put("ms",e.getDuration().toNanos()/1e6);r.put("thread",e.getThread().getJavaName());
                for(var f:e.getEventType().getFields())if(!Set.of("startTime","duration","eventThread","stackTrace").contains(f.getName()))r.put(f.getName(),e.getValue(f.getName()));events.add(r);
            }}
            output.put("status","complete");
        }catch(Exception e){output.put("status","failed");output.put("error",e.toString());e.printStackTrace();}
        finally{
            try{Files.writeString(Path.of("locate.json"),new GsonBuilder().setPrettyPrinting().create().toJson(output));}
            catch(Exception e){throw new IllegalStateException(e);}
            finally{try{CreekFixtureChunks.settle(level,"locate-stop");}finally{server.halt(false);}}
        }
    }
}
