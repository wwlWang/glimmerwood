package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Two warm-up sites then three measured sites per kind; no JFR or phase logging. */
final class CreekAcceptanceAudit {
    static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();var report=new LinkedHashMap<String,Object>();
        var sites=new ArrayList<Map<String,Object>>();report.put("seed",level.getSeed());report.put("sites",sites);
        try{
            if(Boolean.getBoolean("orimod.forestPerformanceIsolation"))report.put("measurementConfiguration",ForestPerformanceConfig.verify("warm"));
            report.put("caveProofNegativeControls",CreekForeignTerrainTrace.verifyCaveProofContract());
            if(Boolean.getBoolean("orimod.profileRuins")||Boolean.getBoolean("orimod.profileRuinSiting"))throw new IllegalStateException("Acceptance forbids phase diagnostics");
            assertNoRecording();report.put("jfr",false);report.put("phaseLogging",false);
            var vm=java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            report.put("vmArguments",vm);
            if(!vm.contains("-Xms2G")||!vm.contains("-Xmx2G")||!vm.contains("-XX:+AlwaysPreTouch"))
                throw new IllegalStateException("Acceptance needs fixed pre-touched 2G heap");
            var seen=new HashSet<String>();
            for(int round=0;round<5;round++)for(var kind:new RuinKind[]{RuinKind.CREEK,RuinKind.FOREST,RuinKind.CAVE}){
                var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,kind.location()));
                var from=round==0?(kind==RuinKind.CREEK?level.getSharedSpawnPos():new BlockPos(0,110,128)):new BlockPos(round*4096,110,round*3072);
                long start=System.nanoTime();var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),from,100,false);
                double locate=(System.nanoTime()-start)/1e6;
                if(found==null)throw new IllegalStateException("No natural "+kind.id);
                var cp=new ChunkPos(found.getFirst());var structure=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
                var piece=structure.getPieces().stream().filter(RuinPiece.class::isInstance).map(RuinPiece.class::cast).findFirst().orElseThrow();
                if(!seen.add(kind.id+":"+piece.origin()))throw new IllegalStateException("Repeated site in acceptance "+piece.origin());
                var b=piece.getBoundingBox();var chunks=new ArrayList<ChunkPos>();
                for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++)chunks.add(new ChunkPos(x,z));
                chunks.sort(Comparator.comparingInt(p->Math.max(Math.abs(p.x-cp.x),Math.abs(p.z-cp.z))));
                // Diagnostic baseline is read before all FEATURES and outside
                // the measured write calls. Unknown/already-decorated columns
                // cannot be used to waive any natural-floor witness.
                var baseline=kind==RuinKind.CREEK?CreekNaturalBaseline.capture(level,piece):null;
                CreekGuardEvidence.checkpoint(level,piece,"CARVERS");
                int prepared=0;
                for(var p:chunks){
                    level.getChunk(p.x,p.z,ChunkStatus.FULL,true);
                    // OuterTimer stops when postProcess returns; this wait is outside it.
                    GameTestDiskBackpressure.preparedChunk(level,++prepared,"acceptance-prepare");
                }
                CreekGuardEvidence.checkpoint(level,piece,"AFTER_FULL");
                var origin=piece.origin();var slices=RuinOuterTimer.snapshot().stream().filter(s->s.kind().equals(kind.id)&&s.originX()==origin.getX()&&s.originY()==origin.getY()&&s.originZ()==origin.getZ()).toList();
                if(slices.isEmpty())throw new IllegalStateException("No outer timing for "+kind.id);
                var row=new LinkedHashMap<String,Object>();sites.add(row);row.put("round",round);row.put("warmup",round<2);row.put("kind",kind.id);row.put("origin",List.of(origin.getX(),origin.getY(),origin.getZ()));row.put("rotation",piece.getRotation().name());row.put("locateMs",locate);
                row.put("slices",slices);row.put("heaviest",slices.stream().max(Comparator.comparingLong(RuinOuterTimer.Slice::nanos)).orElseThrow());
                row.put("jitTotalMillisAfter",java.lang.management.ManagementFactory.getCompilationMXBean().getTotalCompilationTime());
                row.put("heapUsedAfter",java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                row.put("gcLifetimeAfter",java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .map(gc->Map.of("name",gc.getName(),"count",gc.getCollectionCount(),"millis",gc.getCollectionTime())).toList());
                if(kind==RuinKind.CREEK){row.put("naturalTerrain",CreekTerrainGameTests.naturalScan(level,piece,baseline));row.put("beforeFeaturesWater",baseline.report(level));}
                if(kind==RuinKind.CREEK&&RuinFreezeSnapshot.enabled())row.put("freeze",RuinFreezeSnapshot.save(level,b,"natural-"+level.getSeed()+"-"+round));
                // Save partial evidence and drain normal generation continuations
                // outside every measured interval. A 2G heap must not retain all
                // fifteen remote fixture regions until shutdown.
                Files.writeString(ReportFiles.output(Path.of("creek-acceptance.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report));
                System.out.println("CREEK_ACCEPTANCE_SITE round="+round+" warmup="+(round<2)+" kind="+kind.id+" max="+row.get("heaviest"));
                CreekFixtureChunks.settle(level,"acceptance-site");
                CreekGuardEvidence.checkpoint(level,piece,"AFTER_SCAN_AND_SETTLE");
                for(int tick=0;tick<4;tick++){level.getChunkSource().tick(()->true,false);CreekFixtureChunks.settle(level,"acceptance-unload");}
                GameTestDiskBackpressure.drain(level,"acceptance-site-complete");
            }
            assertNoRecording();report.put("status","complete");
        }catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{
            try{Files.writeString(ReportFiles.output(Path.of("creek-acceptance.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(Exception e){throw new IllegalStateException(e);}
            finally{try{CreekGuardEvidence.finishFull();}finally{server.halt(false);}}
        }
    }
    private static void assertNoRecording(){
        if(jdk.jfr.FlightRecorder.isInitialized()&&!jdk.jfr.FlightRecorder.getFlightRecorder().getRecordings().isEmpty())throw new IllegalStateException("JFR recording active during acceptance");
    }
}
