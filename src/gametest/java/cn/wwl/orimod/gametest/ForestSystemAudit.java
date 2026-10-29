package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Gate 4 CPU audit. The same bytecode invokes the actual production entry and
 * builder on both cd1670b and the proposal; no reimplementation of R2 policies. */
final class ForestSystemAudit {
    private static final boolean BREAKDOWN=Boolean.getBoolean("orimod.forestStartBreakdown");
    private final ServerLevel level;
    private final StructureSet set;
    private final Method find;
    private final java.lang.management.ThreadMXBean cpu=ManagementFactory.getThreadMXBean();

    private ForestSystemAudit(ServerLevel level) throws ReflectiveOperationException {
        this.level=level;
        set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins"));
        find=RuinStructure.class.getDeclaredMethod("findGenerationPoint",Structure.GenerationContext.class);
        find.setAccessible(true);
        if(!cpu.isCurrentThreadCpuTimeSupported())throw new IllegalStateException("CPU timer unavailable");
        cpu.setThreadCpuTimeEnabled(true);
    }
    private Structure.GenerationContext context(RuinStructure ruin,ChunkPos cp){
        var source=level.getChunkSource();var g=source.getGenerator();
        return new Structure.GenerationContext(level.registryAccess(),g,g.getBiomeSource(),source.randomState(),
                level.getStructureManager(),level.getSeed(),cp,level,ruin.biomes()::contains);
    }
    private void clearSelectionCaches() throws ReflectiveOperationException {
        var state=level.getChunkSource().randomState();
        var worlds=RuinSitingSites.class.getDeclaredField("WORLDS");worlds.setAccessible(true);
        ((Map<?,?>)worlds.get(null)).remove(state);
        // B shares exact noise cells across all ruin candidates. Formal cold
        // starts must clear this cache too; an A baseline has no such field.
        try{
            var cells=Class.forName("cn.wwl.orimod.world.CreekSurfaceSample").getDeclaredField("WORLDS");
            cells.setAccessible(true);((Map<?,?>)cells.get(null)).remove(state);
        }catch(NoSuchFieldException oldBaseline){}
        for(var entry:set.structures())for(String name:List.of("creekReservations","forestReservations")){
            try{
                var field=RuinStructure.class.getDeclaredField(name);field.setAccessible(true);var reservations=field.get(entry.structure().value());
                var states=reservations.getClass().getDeclaredField("states");states.setAccessible(true);((Map<?,?>)states.get(reservations)).remove(state);
            }catch(NoSuchFieldException absent){if(!name.equals("forestReservations"))throw absent;}
        }
    }
    private List<Map<String,Object>> verifyR2Reference(JsonObject reference) {
        var checked=new ArrayList<Map<String,Object>>();var unique=new HashSet<String>();
        for(var value:reference.getAsJsonArray("r2Costs")){
            var row=value.getAsJsonObject();if(!row.get("pass").getAsString().equals("first"))continue;
            var at=row.getAsJsonArray("chunk");var cp=new ChunkPos(at.get(0).getAsInt(),at.get(1).getAsInt());
            String kind=row.get("kind").getAsString();
            if(!unique.add(kind+":"+cp))throw new IllegalStateException("Duplicate R2 reference "+cp);
            var ruin=(RuinStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .get(ResourceLocation.fromNamespaceAndPath("orimod",kind));
            var base=ruin.decision(context(ruin,cp),ruin.kind.currentVersion());
            if(base.site()==null)throw new IllegalStateException("R2 reference base rejected "+cp+" "+kind+" "+base.reason());
            var actual=RuinSitingSites.yield(context(ruin,cp),base.site());
            boolean expected=row.get("denied").getAsBoolean();
            if(actual.denied()!=expected)throw new IllegalStateException("R2 reference drift "+cp+" "+kind);
            checked.add(Map.of("chunk",List.of(cp.x,cp.z),"kind",kind,"expectedDenied",expected,
                    "actualDenied",actual.denied(),"neighbour",String.valueOf(actual.neighbour()),"neighbourKind",actual.kind()));
        }
        if(checked.size()!=reference.get("unprunedR2Comparisons").getAsInt())throw new IllegalStateException("Incomplete R2 reference");
        return checked;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> start(ChunkPos cp) throws ReflectiveOperationException {
        // Summary row allocation is outside the timing boundary. B-only explanatory event
        // records add overhead and therefore never participate in CPU A/B acceptance.
        if(BREAKDOWN)ForestStartTiming.begin();
        long wall=System.nanoTime(),clock=cpu.getCurrentThreadCpuTime(),decisionCpu=0,decisionWall=0,builderCpu=0,builderWall=0;
        var entries=new ArrayList<>(set.structures());
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum(),tries=0;
        String selected="NONE",first=null;List<RuinDecision.Box> boxes=List.of();
        while(!entries.isEmpty()){
            int roll=random.nextInt(total),i=0;for(var e:entries){roll-=e.weight();if(roll<0)break;i++;}
            var e=entries.remove(i);total-=e.weight();var ruin=(RuinStructure)e.structure().value();
            if(first==null)first=ruin.kind.id;
            long dw=System.nanoTime(),dc=cpu.getCurrentThreadCpuTime();
            var stub=(Optional<Structure.GenerationStub>)find.invoke(ruin,context(ruin,cp));
            decisionCpu+=cpu.getCurrentThreadCpuTime()-dc;decisionWall+=System.nanoTime()-dw;tries++;
            if(stub.isEmpty())continue;
            long bw=System.nanoTime(),bc=cpu.getCurrentThreadCpuTime();
            var pieces=stub.get().getPiecesBuilder().build().pieces();
            builderCpu=cpu.getCurrentThreadCpuTime()-bc;builderWall=System.nanoTime()-bw;
            selected=ruin.kind.id;boxes=pieces.stream().map(p->RuinDecision.Box.of(p.getBoundingBox())).toList();break;
        }
        double totalCpu=(cpu.getCurrentThreadCpuTime()-clock)/1e6,totalWall=(System.nanoTime()-wall)/1e6;
        var details=BREAKDOWN?ForestStartTiming.finish():null;
        var phases=details==null?Map.<String,Double>of():details.phases();
        var row=new LinkedHashMap<String,Object>();row.put("chunk",List.of(cp.x,cp.z));row.put("first",first);row.put("kind",selected);
        row.put("tries",tries);row.put("decisionCpuMs",decisionCpu/1e6);row.put("decisionWallMs",decisionWall/1e6);
        row.put("builderCpuMs",builderCpu/1e6);row.put("builderWallMs",builderWall/1e6);
        row.put("totalCpuMs",totalCpu);row.put("totalWallMs",totalWall);row.put("boxes",boxes);
        if(BREAKDOWN){
            row.put("phases",phases);
            row.put("neighbourPredictions",details.predictions());row.put("ownQualifications",details.ownQualifications());
            row.put("otherDecisionWallMs",Math.max(0,decisionWall/1e6-phases.get("ownForestTerrainMs")-phases.get("ownExternalQualificationMs")-phases.get("r2PredictionMs")));
        }
        return row;
    }
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"forest-system-audit-worker"));
        try{executor.submit(()->{try{
            report.put("measurementConfiguration",ForestPerformanceConfig.verify(BREAKDOWN?"breakdown":"system"));
            report.put("breakdownEnabled",BREAKDOWN);
            report.put("cpuAcceptanceEligible",!BREAKDOWN);
            var vm=ManagementFactory.getRuntimeMXBean().getInputArguments();report.put("vmArguments",vm);
            if(!vm.contains("-Xms2G")||!vm.contains("-Xmx2G")||!vm.contains("-XX:+AlwaysPreTouch"))throw new IllegalStateException("Fixed pre-touched heap required");
            if(Boolean.getBoolean("orimod.profileRuins")||Boolean.getBoolean("orimod.profileRuinSiting")
                    ||jdk.jfr.FlightRecorder.isInitialized()&&!jdk.jfr.FlightRecorder.getFlightRecorder().getRecordings().isEmpty())
                throw new IllegalStateException("No JFR or phase logging during CPU acceptance");
            var audit=new ForestSystemAudit(server.overworld());
            // Remove spawn-area lookahead before measurement, identically for A and B.
            audit.clearSelectionCaches();
            var reference=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.forestSystemReference")))).getAsJsonObject();
            var expected=reference.getAsJsonArray(reference.has("rows")?"rows":"selectedRows");
            if(expected.size()!=2025)throw new IllegalStateException("Expected the original 2025 grid");
            report.put("seed",server.overworld().getSeed());report.put("thread",Thread.currentThread().getName());
            report.put("scope","actual findGenerationPoint + actual builder; identical weighted grid traversal; no postProcess or other structure sets");
            var rows=new ArrayList<Map<String,Object>>();var counts=new TreeMap<String,Integer>();var transitions=new TreeMap<String,Integer>();
            report.put("rows",rows);report.put("counts",counts);report.put("transitions",transitions);
            if(Boolean.getBoolean("orimod.forestR2ReferenceOnly")){
                report.put("r2ReferenceChecks",audit.verifyR2Reference(reference));
                report.put("status","complete");return;
            }
            ForestNoiseThreadAudit.begin();
            String coldCandidates=System.getProperty("orimod.forestColdCandidates");
            if(coldCandidates!=null){
                if(BREAKDOWN)throw new IllegalStateException("Cold acceptance excludes split observers");
                var candidates=JsonParser.parseString(Files.readString(Path.of(coldCandidates))).getAsJsonObject();
                report.put("candidateSelection",candidates);
                var warmup=new ArrayList<Map<String,Object>>();report.put("warmup",warmup);
                for(int i=0;i<12;i++){audit.clearSelectionCaches();warmup.add(audit.start(new ChunkPos(78,79)));}
                report.put("cacheScope","All selection/plan/base and external States cleared before EVERY measured start; JIT warmed 12 times");
                for(var candidate:candidates.getAsJsonArray("candidates")){
                    var p=candidate.getAsJsonObject().getAsJsonArray("chunk");var cp=new ChunkPos(p.get(0).getAsInt(),p.get(1).getAsInt());
                    var ref=expected.asList().stream().map(JsonElement::getAsJsonObject).filter(r->r.get("chunk").equals(p)).findFirst().orElseThrow();
                    for(int round=0;round<3;round++){
                        audit.clearSelectionCaches();var row=audit.start(cp);row.put("round",round+1);rows.add(row);
                        if(!row.get("kind").equals(ref.get("kind").getAsString()))throw new IllegalStateException("Cold selection drift at "+cp);
                        report.put("topFiveStarts",rows.stream().sorted(Comparator.comparingDouble((Map<String,Object> r)->((Number)r.get("totalWallMs")).doubleValue()).reversed()).limit(5).toList());
                        System.out.println("FOREST_COLD chunk="+cp+" round="+(round+1)+" ms="+row.get("totalWallMs"));write(report);
                        if(((Number)row.get("totalWallMs")).doubleValue()>2000){
                            report.put("noiseCallsByThread",ForestNoiseThreadAudit.finish());report.put("status","threshold_failed");
                            report.put("error","Cold whole start exceeds 2000 ms at "+cp+"; remaining acceptance stopped");return;
                        }
                    }
                }
                report.put("noiseCallsByThread",ForestNoiseThreadAudit.finish());report.put("status","complete");return;
            }
            String focus=System.getProperty("orimod.forestProfileChunk");
            if(focus!=null){
                if(!BREAKDOWN)throw new IllegalStateException("Focused cold/hot probe is B-only breakdown, not CPU A/B");
                var xy=focus.split(",");var cp=new ChunkPos(Integer.parseInt(xy[0]),Integer.parseInt(xy[1]));
                audit.clearSelectionCaches();var cold=audit.start(cp);var hot=audit.start(cp);
                report.put("focusedColdStart",cold);report.put("focusedHotStart",hot);
                report.put("focusedCacheScope","All ruin base/plan/prediction caches and per-registry external qualification/biome/placement States cleared for this RandomState; hot immediately repeats actual findGenerationPoint and builder");
                if(!cold.get("kind").equals(hot.get("kind"))||!cold.get("boxes").equals(hot.get("boxes")))throw new IllegalStateException("Cold/hot start drift");
                if(((Number)cold.get("totalWallMs")).doubleValue()>2000){
                    report.put("noiseCallsByThread",ForestNoiseThreadAudit.finish());report.put("status","threshold_failed");
                    report.put("error","Focused cold whole start exceeded 2000 ms; remaining grid and acceptance queue stopped");return;
                }
                audit.clearSelectionCaches();
            }
            for(var value:expected){
                var ref=value.getAsJsonObject();var p=ref.getAsJsonArray("chunk");var cp=new ChunkPos(p.get(0).getAsInt(),p.get(1).getAsInt());
                var row=audit.start(cp);rows.add(row);var kind=(String)row.get("kind");counts.merge(kind,1,Integer::sum);
                transitions.merge(row.get("first")+" -> "+kind,1,Integer::sum);
                if(!kind.equals(ref.get("kind").getAsString()))throw new IllegalStateException("Selected kind drift at "+cp+": "+kind+" / "+ref.get("kind"));
                if(BREAKDOWN&&((Number)row.get("totalWallMs")).doubleValue()>2000){
                    report.put("topFiveStarts",rows.stream().sorted(Comparator.comparingDouble((Map<String,Object> r)->((Number)r.get("totalWallMs")).doubleValue()).reversed()).limit(5).toList());
                    report.put("noiseCallsByThread",ForestNoiseThreadAudit.finish());report.put("status","threshold_failed");
                    report.put("error","Whole start exceeded 2000 ms at "+cp+"; remaining grid and acceptance queue stopped");return;
                }
                if(rows.size()%250==0){write(report);System.out.println("FOREST_SYSTEM grid="+rows.size()+" counts="+counts);}
            }
            var noise=ForestNoiseThreadAudit.finish();report.put("noiseCallsByThread",noise);
            if(noise.keySet().stream().anyMatch(t->!t.equals("forest-system-audit-worker")))
                throw new IllegalStateException("Noise sampling escaped worker: "+noise);
            report.put("mainThreadNoiseCalls",0);
            report.put("totalCpuMs",rows.stream().mapToDouble(r->((Number)r.get("totalCpuMs")).doubleValue()).sum());
            report.put("topFiveStarts",rows.stream().sorted(Comparator.comparingDouble((Map<String,Object> r)->((Number)r.get("totalWallMs")).doubleValue()).reversed()).limit(5).toList());
            report.put("maximumStartWallMs",rows.stream().mapToDouble(r->((Number)r.get("totalWallMs")).doubleValue()).max().orElse(0));
            if(BREAKDOWN)report.put("phaseScope","B-only diagnostic, excluded from A/B acceptance: own forest terrain excludes own external qualification; R2 includes all neighbour prediction and its external qualification; builder and other decision costs are separate");
            report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> report){try{Files.writeString(Path.of("forest-system.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
}
