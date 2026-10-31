package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Same true-grid weighted STRUCTURE_STARTS decisions + builders, with/without R2.
 * Other structure sets and postProcess writes are deliberately outside both totals. */
final class CreekSitingSystemAudit {
    private final ServerLevel level;
    private final StructureSet set;
    private final boolean yielding;
    private final java.lang.management.ThreadMXBean cpu=ManagementFactory.getThreadMXBean();
    private final Map<String,RuinStructure.SiteResult> prepaid=new LinkedHashMap<>();
    private final Map<String,List<Integer>> prepaidPositions=new LinkedHashMap<>();
    private final Method cacheWorld,cacheCompleted,evaluate;
    private final Field terrain;
    private final Constructor<?> kindKey;
    CreekSitingSystemAudit(ServerLevel level,boolean yielding)throws Exception{
        this.level=level;this.yielding=yielding;
        set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins"));
        if(!cpu.isCurrentThreadCpuTimeSupported())throw new IllegalStateException("CPU timer unavailable");
        cpu.setThreadCpuTimeEnabled(true);
        cacheWorld=RuinSitingSites.class.getDeclaredMethod("world",net.minecraft.world.level.levelgen.RandomState.class);cacheWorld.setAccessible(true);
        terrain=cacheWorld.getReturnType().getDeclaredField("terrain");terrain.setAccessible(true);
        cacheCompleted=terrain.getType().getDeclaredMethod("completed",Object.class);cacheCompleted.setAccessible(true);
        kindKey=Class.forName("cn.wwl.orimod.world.RuinSitingSites$KindKey").getDeclaredConstructors()[0];kindKey.setAccessible(true);
        evaluate=RuinStructure.class.getDeclaredMethod("evaluate",Structure.GenerationContext.class,int.class);evaluate.setAccessible(true);
    }
    private RuinStructure structure(RuinKind kind){return (RuinStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());}
    private Structure.GenerationContext context(RuinKind kind,ChunkPos cp){
        var source=level.getChunkSource();var generator=source.getGenerator();
        return new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),source.randomState(),level.getStructureManager(),level.getSeed(),cp,level,structure(kind).biomes()::contains);
    }
    private RuinStructure.SiteResult cached(RuinKind kind,ChunkPos cp)throws Exception{
        var world=cacheWorld.invoke(null,level.getChunkSource().randomState());
        return (RuinStructure.SiteResult)cacheCompleted.invoke(terrain.get(world),kindKey.newInstance(kind.location(),kind.currentVersion(),cp.toLong()));
    }
    private Map<String,Object> start(ChunkPos cp)throws Exception{
        long start=System.nanoTime(),startCpu=cpu.getCurrentThreadCpuTime(),ownCpu=0,ownWall=0,neighbourCpu=0,neighbourWall=0,builderCpu=0,builderWall=0;
        int hits=0,checks=0,queried=0;RuinDecision selected=null;
        var entries=new ArrayList<>(set.structures());var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while(!entries.isEmpty()){
            int roll=random.nextInt(total),i=0;for(var e:entries){roll-=e.weight();if(roll<0)break;i++;}
            var e=entries.get(i);var kind=((RuinStructure)e.structure().value()).kind;var c=context(kind,cp);
            var previous=cached(kind,cp);
            if(previous!=null){hits++;String key=kind.id+":"+cp.toLong();prepaid.put(key,previous);prepaidPositions.put(key,List.of(cp.x,cp.z));}
            long wall=System.nanoTime(),clock=cpu.getCurrentThreadCpuTime();
            var result=structure(kind).decision(c,kind.currentVersion());
            ownCpu+=cpu.getCurrentThreadCpuTime()-clock;ownWall+=System.nanoTime()-wall;checks++;
            if(result.site()!=null){
                boolean denied=false;
                if(yielding&&kind==RuinKind.CREEK){
                    wall=System.nanoTime();clock=cpu.getCurrentThreadCpuTime();var gate=RuinSitingSites.yield(c,result.site());
                    neighbourCpu+=cpu.getCurrentThreadCpuTime()-clock;neighbourWall+=System.nanoTime()-wall;queried+=gate.queried();denied=gate.denied();
                }
                if(!denied){selected=result.site();break;}
            }
            entries.remove(i);total-=e.weight();
        }
        if(selected!=null){
            long wall=System.nanoTime(),clock=cpu.getCurrentThreadCpuTime();
            var pieces=selected.stub(context(selected.kind(),cp)).getPiecesBuilder().build().pieces();
            builderCpu=cpu.getCurrentThreadCpuTime()-clock;builderWall=System.nanoTime()-wall;
            if(!pieces.stream().map(p->RuinDecision.Box.of(p.getBoundingBox())).toList().equals(selected.boxes()))throw new IllegalStateException("System builder bounds mismatch "+cp);
        }
        long elapsedCpu=cpu.getCurrentThreadCpuTime()-startCpu,elapsedWall=System.nanoTime()-start;
        var out=new LinkedHashMap<String,Object>();out.put("chunk",List.of(cp.x,cp.z));out.put("kind",selected==null?"NONE":selected.kind().id);
        out.put("ownChecks",checks);out.put("prepaidOwnHits",hits);out.put("queriedNeighbours",queried);
        out.put("ownCpuMs",ownCpu/1e6);out.put("ownWallMs",ownWall/1e6);out.put("neighbourCpuMs",neighbourCpu/1e6);out.put("neighbourWallMs",neighbourWall/1e6);
        out.put("builderCpuMs",builderCpu/1e6);out.put("builderWallMs",builderWall/1e6);out.put("totalCpuMs",elapsedCpu/1e6);out.put("totalWallMs",elapsedWall/1e6);return out;
    }
    @SuppressWarnings("unchecked") static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"creek-system-siting-audit-worker"));
        try{executor.submit(()->{try{
            boolean yielding=Boolean.getBoolean("orimod.systemYield");var audit=new CreekSitingSystemAudit(server.overworld(),yielding);
            // Discard spawn-area lookahead only, before timing. Each process has
            // the same seed, fixed heap, startup and true-grid traversal order.
            var worlds=RuinSitingSites.class.getDeclaredField("WORLDS");worlds.setAccessible(true);((Map<?,?>)worlds.get(null)).remove(server.overworld().getChunkSource().randomState());
            var reference=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.lightNeighbourReference")))).getAsJsonObject().getAsJsonObject("groups").getAsJsonObject("grid/c");
            report.put("seed",server.overworld().getSeed());report.put("yield",yielding);report.put("thread",Thread.currentThread().getName());
            report.put("scope","spirit_ruins STRUCTURE_STARTS weighted decisions plus piece builders; other sets/postProcess excluded on both sides");
            var rows=new ArrayList<Map<String,Object>>();report.put("rows",rows);var counts=new LinkedHashMap<String,Integer>();report.put("counts",counts);
            var denials=new HashMap<Long,Boolean>();for(var v:reference.getAsJsonObject("boundedRules").getAsJsonObject("R2").getAsJsonArray("firstPassCosts")){var r=v.getAsJsonObject();denials.put(chunk(r).toLong(),r.get("deny").getAsBoolean());}
            var fallback=new HashMap<Long,String>();for(var v:reference.getAsJsonObject("yield").getAsJsonArray("creekDetails")){var r=v.getAsJsonObject();fallback.put(chunk(r).toLong(),r.getAsJsonObject("final").get("kind").getAsString());}
            for(var value:reference.getAsJsonObject("distribution").getAsJsonArray("rows")){
                var r=value.getAsJsonObject();var cp=chunk(r);var row=audit.start(cp);rows.add(row);String kind=(String)row.get("kind");counts.merge(kind,1,Integer::sum);
                String expected=r.getAsJsonObject("selected").get("kind").getAsString();
                if(yielding&&expected.equals("creek_ruins")&&Boolean.TRUE.equals(denials.get(cp.toLong())))expected=fallback.get(cp.toLong());
                if(!kind.equals(expected))throw new IllegalStateException("System selected kind differs at "+cp+": "+kind+" / "+expected);
                if(rows.size()%250==0){write(report);System.out.println("SYSTEM_SITING "+yielding+" "+rows.size()+" "+counts);}
            }
            var checks=new ArrayList<Map<String,Object>>();report.put("prepaidIndependentChecks",checks);
            // Untimed: every actual owner hit must match a fresh independent
            // decision, including its origin, rotation, saved approach and noise samples.
            for(var entry:audit.prepaid.entrySet()){
                var key=entry.getKey();var kind=RuinKind.byId(key.substring(0,key.indexOf(':')));var at=audit.prepaidPositions.get(key);var cp=new ChunkPos(at.get(0),at.get(1));
                var c=audit.context(kind,cp);var biomeState=CreekAuditBiomeCache.capture(c.biomeSource());
                try{
                    var independent=(RuinStructure.SiteResult)audit.evaluate.invoke(audit.structure(kind),c,kind.currentVersion());
                    if(!Objects.equals(entry.getValue(),independent))throw new IllegalStateException("Cached owner decision differs "+key+": cached="+entry.getValue()+" fresh="+independent);
                    checks.add(Map.of("kind",kind.id,"chunk",at,"reason",independent.reason()));
                }finally{biomeState.restore();}
            }
            report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static ChunkPos chunk(JsonObject r){var p=r.getAsJsonArray("chunk");return new ChunkPos(p.get(0).getAsInt(),p.get(1).getAsInt());}
    private static void write(Map<String,Object> r){try{Files.writeString(ReportFiles.output(Path.of("system-siting.json")),new GsonBuilder().setPrettyPrinting().create().toJson(r));}catch(Exception e){throw new RuntimeException(e);}}
}
