package cn.wwl.orimod.gametest;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.block.OriBlocks;
import com.google.gson.GsonBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.*;
import java.util.*;
final class P5WorldgenAudit {
    static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();
        if(Boolean.getBoolean("orimod.worldgen.support.audit")){
            var rows=new ArrayList<Map<String,Object>>();
            int[][] points=level.getSeed()==116555?new int[][]{{-2332,67,-132,-2333,62,-133},{-340,67,1348,-347,62,1358}}
                    :new int[][]{{-594,65,-1382,-596,62,-1388},{2156,67,-2055,2164,62,-2052},{1340,70,700,1339,62,707}};
            for(var xyz:points){
                var origin=new BlockPos(xyz[0],xyz[1],xyz[2]);var at=new BlockPos(xyz[3],xyz[4],xyz[5]);
                var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
                var column=generator.getBaseColumn(at.getX(),at.getZ(),level,random);
                var values=new ArrayList<Map<String,Object>>();
                for(int y=level.getMinBuildHeight();y<=origin.getY()+2;y++)values.add(Map.of("y",y,"actual",level.getBlockState(at.atY(y)).toString(),"noise",column.getBlock(y).toString()));
                rows.add(Map.of("origin",origin.toShortString(),"firstWater",at.toShortString(),"columns",values));
            }
            try{Files.writeString(Path.of("p5-support-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("seed",level.getSeed(),"rows",rows)));}
            catch(Exception ex){throw new IllegalStateException(ex);}
            server.halt(false);return;
        }
        if("p5_startup".equals(System.getProperty("orimod.worldgen.audit.mode"))){
            var times=new ArrayList<Double>();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{
                if(e.getServer()!=server||times.size()>=200)return;
                times.add(server.getTickTimesNanos()[server.getTickCount()%100]/1e6);
                if(times.size()==200){
                    var data=GreatTreeSite.get(level);var report=new LinkedHashMap<String,Object>();
                    report.put("seed",level.getSeed());report.put("site",data.site()==null?null:data.site().origin().toShortString());
                    report.put("noSite",data.noSite());report.put("completedRange",data.completedRange());
                    report.put("ticks",times);report.put("maxTickMs",Collections.max(times));report.put("status","complete");
                    save(report);server.halt(false);
                }
            });
            return;
        }
        if("p5_locate".equals(System.getProperty("orimod.worldgen.audit.mode"))){
            if(Boolean.getBoolean("orimod.worldgen.profile.forests")){
                try(var recording=new jdk.jfr.Recording()){
                    recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(2));
                    recording.enable("orimod.RuinSiting");recording.start();
                    var rows=new ArrayList<Map<String,Object>>();
                    for(int i=0;i<6;i++){
                        var env=new ForestExternalReservationGameTests.Environment(level,5555);
                        var forest=(RuinStructure)env.structure("orimod:forest_spring");
                        var method=RuinStructure.class.getDeclaredMethod("evaluate",net.minecraft.world.level.levelgen.structure.Structure.GenerationContext.class,int.class);method.setAccessible(true);
                        var context=env.context(forest,new ChunkPos(-5776,-2800));long before=System.nanoTime();
                        var result=(RuinStructure.SiteResult)method.invoke(forest,context,forest.kind.currentVersion());
                        rows.add(Map.of("ms",(System.nanoTime()-before)/1e6,"reason",result.reason()));
                    }
                    recording.stop();recording.dump(Path.of("forest-prediction.jfr"));save(Map.of("status","complete","rows",rows));
                }catch(Exception e){throw new IllegalStateException(e);}
                server.halt(false);return;
            }

            var results=new ArrayList<Map<String,Object>>();
            String only=System.getProperty("orimod.worldgen.locate.kind","");
            boolean expanded=!only.isEmpty();
            var directions=new java.util.Random(117005L);
            var queries=new ArrayList<BlockPos>();
            for(int i=0;i<(expanded?8:2);i++){
                if(expanded){
                    double angle=(i+0.15+directions.nextDouble()*0.7)*Math.PI/4;
                    double radius=100000+directions.nextInt(100000);
                    queries.add(new BlockPos((int)Math.round(Math.cos(angle)*radius),64,(int)Math.round(Math.sin(angle)*radius)));
                }else queries.add(level.getSharedSpawnPos().offset(100000+i*10000,0,100000));
            }
            for(var query:queries)for(String kind:expanded?List.of(only):List.of("spirit_tree","blighted_grounds")){
                var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                var holder=registry.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("orimod",kind)));
                System.out.println("P5_LOCATE_BEGIN seed="+level.getSeed()+" kind="+kind+" query="+query.toShortString());
                long start=System.nanoTime();
                var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),query,100,false);
                var row=new LinkedHashMap<String,Object>();row.put("kind",kind);row.put("query",query.toShortString());row.put("found",found==null?null:found.getFirst().toShortString());row.put("ms",(System.nanoTime()-start)/1e6);results.add(row);
                System.out.println("P5_COLD_LOCATE "+row);save(Map.of("status","running","seed",level.getSeed(),"rows",results));
            }
            save(Map.of("status","complete","seed",level.getSeed(),"rows",results));server.halt(false);return;
        }

        var report=new LinkedHashMap<String,Object>();var rows=new ArrayList<Map<String,Object>>();
        report.put("seed",level.getSeed());report.put("status","running");report.put("rows",rows);
        report.put("method","4x4 locate query grid, step 1024, centered on actual spawn; deduplicated registered starts; actual FULL 3x3 generation and heart/core journal+block check. Not a preflight prediction.");
        var seen=new HashSet<String>();long begin=System.nanoTime();
        try{
            var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for(int z=-2;z<2;z++)for(int x=-2;x<2;x++)for(String kind:List.of("spirit_tree","blighted_grounds")){
                var query=level.getSharedSpawnPos().offset(x*1024+512,0,z*1024+512);
                var holder=registry.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("orimod",kind)));
                var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),query,100,false);
                if(found==null)throw new IllegalStateException("No locate result: "+kind+" "+query);
                var cp=new ChunkPos(found.getFirst());if(!seen.add(kind+cp))continue;
                var start=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
                if(start==null||!start.isValid())throw new IllegalStateException("locate returned invalid start");
                var piece=start.getPieces().stream().filter(FeatureSitePiece.class::isInstance).map(FeatureSitePiece.class::cast).findFirst().orElseThrow();
                var origin=piece.origin();
                // Request origin FULL first (postProcess happens here), then its complete write neighbourhood.
                level.getChunk(cp.x,cp.z);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk(cp.x+dx,cp.z+dz);
                var journal=new TreeSet<Long>();var type=kind.equals("spirit_tree")?FeatureSites.TREES:FeatureSites.BLIGHTS;
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(long p:level.getChunk(cp.x+dx,cp.z+dz).getData(type)){
                    var at=BlockPos.of(p);if(Math.abs(at.getX()-origin.getX())<=22 && Math.abs(at.getZ()-origin.getZ())<=22 && level.getBlockState(at).is(kind.equals("spirit_tree")?OriBlocks.SPIRIT_TREE_HEART:OriBlocks.GLOOM_CORE))journal.add(p);
                }
                boolean placed=!journal.isEmpty();
                var outcome=Map.of("placed",placed,"reasons",placed?List.of():List.of("NO_SURVIVING_HEART_OR_CORE; see server WARN"));
                var row=new LinkedHashMap<String,Object>();row.put("kind",kind);row.put("origin",origin.toShortString());row.put("chunk",List.of(cp.x,cp.z));
                row.put("biome",level.getBiome(origin).unwrapKey().orElseThrow().location().toString());row.put("outcome",outcome);
                row.put("survivingJournalPositions",journal.stream().map(BlockPos::of).map(BlockPos::toShortString).toList());rows.add(row);save(report);
                System.out.println("P5_FEATURE seed="+level.getSeed()+" n="+rows.size()+" kind="+kind+" placed="+placed+" reasons="+outcome.get("reasons"));
                // Real chunk generation; let normal tickets expire/unload between samples, no edits or ecology ticks.
                GameTestDiskBackpressure.unloadAndDrain(level,"p5-features");
            }
            report.put("status","complete");
        }catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{report.put("elapsedSeconds",(System.nanoTime()-begin)/1e9);save(report);server.halt(false);}
    }
    static void save(Map<String,Object> r){try{Files.writeString(Path.of("p5-worldgen-audit.json"),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(r));}catch(Exception e){throw new IllegalStateException(e);}}
}
