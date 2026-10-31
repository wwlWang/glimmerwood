package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Full production viability + vanilla weighted/fallback selection, no world writes.
 * Report piece-box intersections separately from merely viable creek positions. */
final class CreekActualNeighbourAudit {
    record Site(String kind,int version,String rotation,List<BoundingBox> boxes){}
    private final ServerLevel level;
    private final StructureSet set;
    private final RandomSpreadStructurePlacement placement;
    private final Map<String,Optional<Site>> checks=new HashMap<>(),selected=new HashMap<>();
    private final CreekOffsetAudit offsets;
    private final java.lang.reflect.Method find;
    private long heightChecksNanos;
    CreekActualNeighbourAudit(ServerLevel level)throws Exception{this.level=level;set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins"));placement=(RandomSpreadStructurePlacement)set.placement();offsets=new CreekOffsetAudit(level);find=RuinStructure.class.getDeclaredMethod("findGenerationPoint",Structure.GenerationContext.class,int.class);find.setAccessible(true);}
    private Structure.GenerationContext context(Structure s,ChunkPos cp){var cs=level.getChunkSource();var g=cs.getGenerator();return new Structure.GenerationContext(level.registryAccess(),g,g.getBiomeSource(),cs.randomState(),level.getStructureManager(),level.getSeed(),cp,level,s.biomes()::contains);}
    @SuppressWarnings("unchecked") Optional<Site> check(String policy,RuinKind kind,ChunkPos cp){
        int version=policy.equals("v0")?0:kind.currentVersion();String method=kind==RuinKind.CREEK?policy:"v"+version;
        return checks.computeIfAbsent(method+":"+kind.id+":"+cp.toLong(),unused->{try{
            long start=System.nanoTime();var s=(RuinStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());var c=context(s,cp);List<StructurePiece> pieces;
            if(kind==RuinKind.CREEK&&(policy.equals("c")||policy.equals("e")||policy.startsWith("d"))){
                var result=offsets.sample(cp,CreekOffsetAudit.limit(policy),false);if(!Boolean.TRUE.equals(result.get("accepted")))return Optional.empty();pieces=offsets.materialize(cp,result);
            }else{
                var result=(Optional<Structure.GenerationStub>)find.invoke(s,c,version);if(result.isEmpty())return Optional.empty();
                var at=result.get().position();if(!c.validBiome().test(c.biomeSource().getNoiseBiome(at.getX()>>2,at.getY()>>2,at.getZ()>>2,c.randomState().sampler())))return Optional.empty();
                pieces=result.get().getPiecesBuilder().build().pieces();if(pieces.isEmpty())return Optional.empty();
            }
            heightChecksNanos+=System.nanoTime()-start;
            var ruin=pieces.stream().filter(RuinPiece.class::isInstance).map(RuinPiece.class::cast).findFirst().orElseThrow();
            return Optional.of(new Site(kind.id,version,ruin.getRotation().name(),pieces.stream().map(p->p.getBoundingBox()).toList()));
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    Optional<Site> selected(String policy,ChunkPos cp){return weighted(policy,cp,false);}
    Optional<Site> fallback(String policy,ChunkPos cp){return weighted(policy,cp,true);}
    private Optional<Site> weighted(String policy,ChunkPos cp,boolean banCreek){return selected.computeIfAbsent(policy+(banCreek?"!:":":")+cp.toLong(),unused->{
        var entries=new ArrayList<>(set.structures());var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while(!entries.isEmpty()){
            int roll=random.nextInt(total),i=0;for(var entry:entries){roll-=entry.weight();if(roll<0)break;i++;}
            var entry=entries.get(i);var kind=((RuinStructure)entry.structure().value()).kind;
            var found=banCreek&&kind==RuinKind.CREEK?Optional.<Site>empty():check(policy,kind,cp);if(found.isPresent())return found;
            entries.remove(i);total-=entry.weight();
        }return Optional.empty();
    });}
    ServerLevel level(){return level;}
    int cachedSelected(){return selected.size();}
    int cachedChecks(){return checks.size();}
    boolean potential(ChunkPos cp){return placement.getPotentialStructureChunk(level.getSeed(),cp.x,cp.z).equals(cp);}
    String firstKind(ChunkPos cp){
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
        int roll=random.nextInt(set.structures().stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum());
        for(var entry:set.structures()){roll-=entry.weight();if(roll<0)return ((RuinStructure)entry.structure().value()).kind.id;}
        throw new IllegalStateException("Weighted first roll outside structure set");
    }
    Map<String,Object> distribution(String policy,JsonArray points){
        var counts=new LinkedHashMap<String,Integer>();
        for(String kind:List.of("creek_ruins","forest_spring","cave_altar","NONE"))counts.put(kind,0);
        var transitions=new LinkedHashMap<String,Integer>();
        var rows=new ArrayList<Map<String,Object>>();int excluded=0;
        for(var value:points){
            var q=value.getAsJsonObject().getAsJsonArray("chunk");var cp=new ChunkPos(q.get(0).getAsInt(),q.get(1).getAsInt());
            if(!potential(cp)){excluded++;continue;}
            var site=selected(policy,cp);counts.merge(site.map(Site::kind).orElse("NONE"),1,Integer::sum);
            String first=firstKind(cp);transitions.merge(first+" -> "+site.map(Site::kind).orElse("NONE"),1,Integer::sum);
            rows.add(Map.of("chunk",List.of(cp.x,cp.z),"first",first,"selected",describe(site)));
        }
        if(counts.values().stream().mapToInt(Integer::intValue).sum()+excluded!=points.size())throw new IllegalStateException("Distribution denominator mismatch");
        System.out.println("ACTUAL_DISTRIBUTION "+policy+" sample="+points.size()+" excluded="+excluded+" "+counts);
        return Map.of("samplePoints",points.size(),"potentialStarts",rows.size(),"excludedNonPotential",excluded,"counts",counts,"transitions",transitions,"rows",rows);
    }
    List<ChunkPos> neighbours(ChunkPos cp){
        var out=new ArrayList<ChunkPos>();int gx=Math.floorDiv(cp.x,placement.spacing()),gz=Math.floorDiv(cp.z,placement.spacing());
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0)out.add(placement.getPotentialStructureChunk(level.getSeed(),(gx+dx)*placement.spacing(),(gz+dz)*placement.spacing()));return out;
    }
    static boolean overlap(Site a,Site b,boolean horizontal){for(var x:a.boxes)for(var y:b.boxes)if(horizontal?x.intersects(y.minX(),y.minZ(),y.maxX(),y.maxZ()):x.intersects(y))return true;return false;}
    static Map<String,Object> describe(Optional<Site> site){return site.<Map<String,Object>>map(s->Map.of("kind",s.kind,"version",s.version,"rotation",s.rotation,"boxes",boxes(s))).orElse(Map.of("kind","NONE"));}
    private Map<String,Object> sample(String policy,ChunkPos cp){
        var own=check(policy,RuinKind.CREEK,cp);if(own.isEmpty())return Map.of("chunk",List.of(cp.x,cp.z),"creekViable",false);
        var self=selected(policy,cp);boolean realGrid=placement.getPotentialStructureChunk(level.getSeed(),cp.x,cp.z).equals(cp);
        boolean ownChosen=realGrid&&self.isPresent()&&self.get().kind.equals(RuinKind.CREEK.id);
        var neighbours=new ArrayList<Map<String,Object>>();int gx=Math.floorDiv(cp.x,placement.spacing()),gz=Math.floorDiv(cp.z,placement.spacing());
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0)continue;
            var np=placement.getPotentialStructureChunk(level.getSeed(),(gx+dx)*placement.spacing(),(gz+dz)*placement.spacing());var n=selected(policy,np);if(n.isEmpty())continue;
            boolean xz=false,xyz=false;for(var a:own.get().boxes)for(var b:n.get().boxes){if(a.intersects(b.minX(),b.minZ(),b.maxX(),b.maxZ()))xz=true;if(a.intersects(b))xyz=true;}
            neighbours.add(Map.of("chunk",List.of(np.x,np.z),"kind",n.get().kind,"version",n.get().version,"rotation",n.get().rotation,"horizontalOverlap",xz,"writeBoxOverlap",xyz,"boxes",boxes(n.get())));
        }
        return Map.of("chunk",List.of(cp.x,cp.z),"creekViable",true,"ownIsPotential",realGrid,"ownActuallySelected",ownChosen,"ownSelectedKind",self.map(s->s.kind).orElse("NONE"),"ownRotation",own.get().rotation,"ownBoxes",boxes(own.get()),"neighbours",neighbours);
    }
    private static List<List<Integer>> boxes(Site s){return s.boxes.stream().map(b->List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())).toList();}
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();var groups=new LinkedHashMap<String,Object>();report.put("groups",groups);report.put("seed",server.overworld().getSeed());
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"creek-actual-neighbour-audit-worker"));
        try{executor.submit(()->{try{
            var audit=new CreekActualNeighbourAudit(server.overworld());var root=Path.of(System.getProperty("orimod.neighbourAuditRoot"));
            var policies=List.of(System.getProperty("orimod.neighbourAuditPolicies","v0,v1,c,d17,d9,d5,e").split(","));
            if(Boolean.getBoolean("orimod.neighbourYieldAudit"))CreekYieldAudit.selfTest();
            for(String dataset:List.of("uniform","grid"))for(String policy:policies){
                String dir=policy.equals("e")?"offset-"+dataset+"-e-01":policy.startsWith("d")?"offset-"+dataset+"-"+policy.substring(1)+"-01":dataset.equals("uniform")?"candidates-09":"grid-candidates-03";
                String file=(policy.equals("e")||policy.startsWith("d"))?"offsets.json":"candidates.json";
                var source=JsonParser.parseString(Files.readString(root.resolve(dir).resolve(file))).getAsJsonObject();if(!source.get("status").getAsString().equals("complete"))throw new IllegalStateException("Incomplete source "+dir);
                var rows=new ArrayList<Map<String,Object>>();var group=new LinkedHashMap<String,Object>();group.put("rows",rows);group.put("source",dir);groups.put(dataset+"/"+policy,group);int candidates=0;
                for(var value:source.getAsJsonArray("rows")){
                    var r=value.getAsJsonObject();boolean accepted;
                    if(policy.equals("c")){var a=r.getAsJsonObject("allReasonAudit");accepted=true;for(var reason:a.getAsJsonArray("reasons"))if(!reason.getAsString().startsWith("STRUCTURE_"))accepted=false;if(!a.getAsJsonObject("variants").getAsJsonObject("c").getAsJsonArray("reasons").isEmpty())accepted=false;}else accepted=r.get((policy.equals("e")||policy.startsWith("d"))?"accepted":policy).getAsBoolean();
                    r.addProperty("accepted",accepted);if(!accepted)continue;candidates++;
                    var q=r.getAsJsonArray("chunk");var row=audit.sample(policy,new ChunkPos(q.get(0).getAsInt(),q.get(1).getAsInt()));
                    if(!Boolean.TRUE.equals(row.get("creekViable")))throw new IllegalStateException("Accepted creek failed recheck "+policy+" "+q);
                    rows.add(row);if(rows.size()%25==0){write(report);System.out.println("ACTUAL_NEIGHBOURS "+dataset+"/"+policy+" completed="+rows.size());}
                }
                group.put("accepted",candidates);
                if(Boolean.getBoolean("orimod.neighbourYieldAudit"))group.put("distribution",audit.distribution(policy,source.getAsJsonArray("rows")));
                if(dataset.equals("grid")&&Boolean.getBoolean("orimod.neighbourYieldAudit"))group.put("yield",CreekYieldAudit.run(audit,policy,source.getAsJsonArray("rows")));
                if(Boolean.getBoolean("orimod.neighbourYieldAudit"))group.put("boundedRules",CreekBoundedYieldAudit.run(audit,policy,source.getAsJsonArray("rows"),dataset.equals("grid")));
                write(report);System.out.println("ACTUAL_NEIGHBOURS COMPLETE "+dataset+"/"+policy+" "+candidates);
            }
            if(Boolean.getBoolean("orimod.neighbourYieldAudit")){
                // Full denominator, including starts where creek failed. Earlier
                // neighbour rows were conditional on a viable creek and cannot
                // establish forest/cave counts or the world's total density.
                var distributions=new LinkedHashMap<String,Object>();report.put("distributions",distributions);
                for(String dataset:List.of("uniform","grid")){
                    var source=JsonParser.parseString(Files.readString(root.resolve(dataset.equals("grid")?"grid-candidates-03":"candidates-09").resolve("candidates.json"))).getAsJsonObject();
                    var distributionPolicies=Boolean.getBoolean("orimod.neighbourAuditDistributionAll")?List.of("v0","v1","c","d17","d9","d5","e","d4r8","d8r8"):List.of("v0","v1","c");
                    for(String policy:distributionPolicies){
                        distributions.put(dataset+"/"+policy,audit.distribution(policy,source.getAsJsonArray("rows")));write(report);
                    }
                }
            }
            report.put("cachedKindChecks",audit.checks.size());report.put("cachedSelectedStarts",audit.selected.size());report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> report){try{Files.writeString(ReportFiles.output(Path.of("neighbours.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
}
