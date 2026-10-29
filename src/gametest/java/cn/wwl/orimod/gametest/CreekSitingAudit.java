package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import java.time.Duration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Fixed coordinates, both exact production viability paths; no chunks generated or tickets acquired. */
public final class CreekSitingAudit {
    public static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();var report=new LinkedHashMap<String,Object>();
        boolean grid=Boolean.getBoolean("orimod.candidateGrid"),allReasons=Boolean.getBoolean("orimod.candidateAllReasons");
        int target=grid?2025:2000;
        var rows=new ArrayList<Map<String,Object>>();report.put("rows",rows);report.put("seed",level.getSeed());
        report.put("selection","Random(116118): uniform chunks [-1024,1023], distinct; creek-valid biome at column centre y=80; 2000 fixed coordinates");
        if(grid)report.put("selection","Actual spirit_ruins potential chunks: contiguous regions [-22,22] x [-22,22], 45x45=2025, within the old [-1024,1023] chunk domain; no biome prefilter");
        report.put("allReasonsAudit",allReasons);
        report.put("reservationBiomeSeaOffsets",List.of(0,-16,16,32,64));
        report.put("reservationVariants","a=current; b=biome-filtered potential starts; c=b plus exact glades corner envelope vs actual creek rotation; diagnostic only");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"creek-siting-audit-worker"));
        try(var recording=new Recording()){
            recording.enable("orimod.RuinSiting").withThreshold(Duration.ZERO);recording.enable("orimod.CreekTerrainSampling").withThreshold(Duration.ZERO);recording.start();
            executor.submit(()->{
                try{
                    var generator=level.getChunkSource().getGenerator();var randomState=level.getChunkSource().randomState();
                    var ruin=(RuinStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RuinKind.CREEK.location());
                    var overlaps=new CreekOverlapAudit(level);
                    var rejectionAudit=new CreekRejectionAudit();
                    var placement=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                            .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins")).placement();
                    report.put("boundaryProbe",CreekBiomeBoundaryAudit.run(level));
                    var rng=new Random(116118);var seen=new HashSet<Long>();int attempted=0;
                    var check=RuinStructure.class.getDeclaredMethod("findGenerationPoint",Structure.GenerationContext.class,int.class);check.setAccessible(true);
                    String reference=grid?"":System.getProperty("orimod.candidateReference","");
                    var baseline=reference.isEmpty()?null:JsonParser.parseString(Files.readString(Path.of(reference))).getAsJsonObject().getAsJsonArray("rows");
                    if(baseline!=null){if(baseline.size()!=2000)throw new IllegalStateException("Reference needs all 2000 coordinates");report.put("v0Reference",reference);}
                    while(rows.size()<target){
                        int index=rows.size();
                        var cp=grid?placement.getPotentialStructureChunk(level.getSeed(),(index%45-22)*placement.spacing(),(index/45-22)*placement.spacing()):new ChunkPos(rng.nextInt(2048)-1024,rng.nextInt(2048)-1024);attempted++;
                        if(!seen.add(cp.toLong()))continue;
                        var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(cp.getMiddleBlockX()),20,QuartPos.fromBlock(cp.getMiddleBlockZ()),randomState.sampler());
                        if(!grid&&!ruin.biomes().contains(biome))continue;
                        var row=new LinkedHashMap<String,Object>();row.put("chunk",List.of(cp.x,cp.z));row.put("biome",biome.unwrapKey().orElseThrow().location().toString());
                        CreekAuditBiomeCache beforeV1=null,afterV1=null;
                        for(int version=0;version<=1;version++){
                            if(version==0&&baseline!=null){
                                var old=baseline.get(rows.size()).getAsJsonObject();var chunk=old.getAsJsonArray("chunk");
                                if(chunk.get(0).getAsInt()!=cp.x||chunk.get(1).getAsInt()!=cp.z)throw new IllegalStateException("Fixed candidate drift at "+rows.size());
                                row.put("v0",old.get("v0").getAsBoolean());row.put("v0Ms",old.get("v0Ms").getAsDouble());continue;
                            }
                            var context=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),randomState,level.getStructureManager(),level.getSeed(),cp,level,ruin.biomes()::contains);
                            if(version==1)beforeV1=CreekAuditBiomeCache.capture(generator.getBiomeSource());
                            long before=System.nanoTime();@SuppressWarnings("unchecked") var result=(Optional<Structure.GenerationStub>)check.invoke(ruin,context,version);
                            if(version==1)afterV1=CreekAuditBiomeCache.capture(generator.getBiomeSource());
                            row.put("v"+version+"Ms",(System.nanoTime()-before)/1e6);row.put("v"+version,result.isPresent());
                            if(version==1&&result.isPresent()){
                                before=System.nanoTime();var pieces=result.get().getPiecesBuilder().build().pieces();
                                var piece=pieces.stream().filter(RuinPiece.class::isInstance).map(RuinPiece.class::cast).findFirst().orElseThrow();
                                row.put("builderMs",(System.nanoTime()-before)/1e6);row.put("origin",List.of(piece.origin().getX(),piece.origin().getY(),piece.origin().getZ()));row.put("rotation",piece.getRotation().name());
                                var terrain=field(piece,"creekTerrain");var contract=field(terrain,"contract");var rules=invoke(contract,"rules",new Class<?>[0]);
                                var height=terrain.getClass().getSuperclass().getDeclaredMethod("natural",int.class,int.class);height.setAccessible(true);
                                var approach=terrain.getClass().getSuperclass().getDeclaredMethod("approachFloor",int.class,int.class);approach.setAccessible(true);
                                java.util.function.IntBinaryOperator source=(x,z)->(int)reflect(height,terrain,x,z),entry=(x,z)->(int)reflect(approach,terrain,x,z);
                                var plan=(List<?>)invoke(rules,"plan",new Class<?>[]{int.class,int.class,int.class,int.class,java.util.function.IntBinaryOperator.class,java.util.function.IntBinaryOperator.class},-8,-8,52,74,source,entry);
                                long boundary=0,guards=0,noiseGuards=0;var exactHeights=new HashMap<Long,Integer>();
                                for(var c:plan){int x=(int)invoke(c,"x",new Class<?>[0]),z=(int)invoke(c,"z",new Class<?>[0]);
                                    if((boolean)invoke(rules,"outerBoundary",new Class<?>[]{int.class,int.class},x,z))boundary++;
                                    if((boolean)invoke(c,"entrance",new Class<?>[0]))continue;
                                    boolean guard=(boolean)invoke(c,"retaining",new Class<?>[0]);if(guard)guards++;
                                    // Audit-only exact ninth-column noise probes: they do NOT
                                    // count as builder work or production sampling. Natural
                                    // feature ordering is separately checked in real worlds.
                                    for(var dir:Direction.Plane.HORIZONTAL){int nx=x+dir.getStepX(),nz=z+dir.getStepZ();
                                        if(!(boolean)invoke(rules,"readOnlySeam",new Class<?>[]{int.class,int.class},nx,nz))continue;
                                        var at=piece.placement().block(new BlockPos(nx,0,nz));
                                        int actual=exactHeights.computeIfAbsent(at.asLong(),key->generator.getBaseHeight(at.getX(),at.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,randomState)-1-piece.origin().getY());
                                        if((int)invoke(c,"top",new Class<?>[0])-actual>1)guard=true;
                                    }
                                    if(guard)noiseGuards++;
                                }
                                row.put("boundary",boundary);row.put("guards",guards);row.put("guardRatio",(double)guards/boundary);
                                row.put("exactNoiseGuards",noiseGuards);row.put("exactNoiseGuardRatio",(double)noiseGuards/boundary);row.put("auditOnlyExactQueries",exactHeights.size());

                            }
                        }
                        row.put("overlapAudit",overlaps.sample(cp));
                        if(allReasons){
                            var c=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),randomState,level.getStructureManager(),level.getSeed(),cp,level,ruin.biomes()::contains);
                            beforeV1.restore();
                            var diagnostic=rejectionAudit.sample(c);row.put("allReasonAudit",diagnostic);
                            if(Boolean.TRUE.equals(row.get("v1"))!=((List<?>)diagnostic.get("reasons")).isEmpty())
                                throw new IllegalStateException("Exhaustive reasons disagree with production at "+cp+": "+diagnostic+" production="+row.get("v1"));
                        }
                        afterV1.restore();
                        rows.add(row);
                        if(rows.size()%50==0||rows.size()==target){report.put("attempted",attempted);write(report);System.out.println("CREEK_CANDIDATES completed="+rows.size()+" v0="+rows.stream().filter(r->Boolean.TRUE.equals(r.get("v0"))).count()+" v1="+rows.stream().filter(r->Boolean.TRUE.equals(r.get("v1"))).count());}
                    }
                }catch(Exception e){throw new RuntimeException(e);}
            }).get();
            recording.stop();recording.dump(Path.of("candidates.jfr"));
            var events=new ArrayList<Map<String,Object>>();report.put("events",events);
            try(var input=new RecordingFile(Path.of("candidates.jfr"))){while(input.hasMoreEvents()){
                var e=input.readEvent();var row=new LinkedHashMap<String,Object>();row.put("type",e.getEventType().getName());row.put("ms",e.getDuration().toNanos()/1e6);row.put("thread",e.getThread().getJavaName());
                for(var f:e.getEventType().getFields())if(!Set.of("startTime","duration","eventThread","stackTrace").contains(f.getName()))row.put(f.getName(),e.getValue(f.getName()));events.add(row);
            }}
            var first=new HashMap<String,String>();
            for(var e:events)if(e.get("type").equals("orimod.RuinSiting"))first.put(e.get("version")+":"+List.of(e.get("chunkX"),e.get("chunkZ")),(String)e.get("reason"));
            if(report.containsKey("v0Reference"))for(var value:JsonParser.parseString(Files.readString(Path.of((String)report.get("v0Reference")))).getAsJsonObject().getAsJsonArray("events")){
                var e=value.getAsJsonObject();if(e.get("type").getAsString().equals("orimod.RuinSiting")&&e.get("version").getAsInt()==0)
                    first.put("0:"+List.of(e.get("chunkX").getAsInt(),e.get("chunkZ").getAsInt()),e.get("reason").getAsString());
            }
            for(var row:rows){
                for(int v=0;v<=1;v++)row.put("v"+v+"FirstReason",Objects.requireNonNull(first.get(v+":"+row.get("chunk")),"Missing production trace "+row.get("chunk")));
                if(allReasons){
                    var diagnostic=(Map<?,?>)row.get("allReasonAudit");var reasons=(List<?>)diagnostic.get("reasons");
                    if(!row.get("v1FirstReason").equals(reasons.isEmpty()?"PASS":reasons.getFirst()))throw new IllegalStateException("First production reason differs from exhaustive audit: "+row);
                }
            }
            report.put("status","complete");
        }catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static java.lang.reflect.Field lookupField(Class<?> type,String name)throws NoSuchFieldException{try{return type.getDeclaredField(name);}catch(NoSuchFieldException e){if(type.getSuperclass()==null)throw e;return lookupField(type.getSuperclass(),name);}}
    private static java.lang.reflect.Method lookupMethod(Class<?> type,String name,Class<?>[] types)throws NoSuchMethodException{try{return type.getDeclaredMethod(name,types);}catch(NoSuchMethodException e){if(type.getSuperclass()==null)throw e;return lookupMethod(type.getSuperclass(),name,types);}}
    private static Object field(Object o,String name)throws Exception{var f=lookupField(o.getClass(),name);f.setAccessible(true);return f.get(o);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object...args)throws Exception{var m=lookupMethod(o.getClass(),name,types);m.setAccessible(true);return m.invoke(o,args);}
    private static Object reflect(java.lang.reflect.Method m,Object o,Object...args){try{return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private static void write(Map<String,Object> report){try{Files.writeString(Path.of("candidates.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
}
