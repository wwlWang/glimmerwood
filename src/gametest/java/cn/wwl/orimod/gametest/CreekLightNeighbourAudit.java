package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.*;
import java.util.*;

/** Accepted c+R2 policy: cold incremental costs, exact boxes, final distribution and forest rejection reasons. */
final class CreekLightNeighbourAudit {
    private final ServerLevel level;
    private final StructureSet set;
    private final Map<Long,Optional<RuinDecision>> selected=new LinkedHashMap<>();
    private final Map<String,RuinDecision> checked=new LinkedHashMap<>();
    CreekLightNeighbourAudit(ServerLevel level){this.level=level;set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins"));}
    private RuinStructure structure(RuinKind kind){return (RuinStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());}
    private Structure.GenerationContext context(RuinKind kind,ChunkPos pos){
        var g=level.getChunkSource().getGenerator();return new Structure.GenerationContext(level.registryAccess(),g,g.getBiomeSource(),level.getChunkSource().randomState(),
                level.getStructureManager(),level.getSeed(),pos,level,structure(kind).biomes()::contains);
    }
    private RuinStructure.SiteResult base(RuinKind kind,ChunkPos pos){
        var result=structure(kind).decision(context(kind,pos),1);
        if(result.site()!=null)checked.put(kind.id+":"+pos.toLong(),result.site());return result;
    }
    private Optional<RuinDecision> actual(ChunkPos pos){return selected.computeIfAbsent(pos.toLong(),unused->{
        var entries=new ArrayList<>(set.structures());var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),pos.x,pos.z);
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while(!entries.isEmpty()){
            int roll=random.nextInt(total),i=0;for(var e:entries){roll-=e.weight();if(roll<0)break;i++;}
            var e=entries.get(i);var kind=((RuinStructure)e.structure().value()).kind;var result=base(kind,pos);
            if(result.site()!=null&&(kind!=RuinKind.CREEK||!RuinSitingSites.yield(context(kind,pos),result.site()).denied()))return Optional.of(result.site());
            entries.remove(i);total-=e.weight();
        }
        return Optional.empty();
    });}
    private List<ChunkPos> neighbours(ChunkPos cp){
        var p=(RandomSpreadStructurePlacement)set.placement();var out=new ArrayList<ChunkPos>();int gx=Math.floorDiv(cp.x,p.spacing()),gz=Math.floorDiv(cp.z,p.spacing());
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0)out.add(p.getPotentialStructureChunk(level.getSeed(),(gx+dx)*p.spacing(),(gz+dz)*p.spacing()));return out;
    }
    private static ChunkPos chunk(JsonObject row){var a=row.getAsJsonArray("chunk");return new ChunkPos(a.get(0).getAsInt(),a.get(1).getAsInt());}
    private Map<String,Object> measure(ChunkPos cp,boolean build){
        long start=System.nanoTime();var own=base(RuinKind.CREEK,cp);long decision=System.nanoTime();
        if(own.site()==null)throw new IllegalStateException("Reference c acceptance changed at "+cp+": "+own.reason());
        var gate=RuinSitingSites.yield(context(RuinKind.CREEK,cp),own.site());long neighbour=System.nanoTime();
        if(build&&!gate.denied())checkBuilder(own.site(),cp);
        long end=System.nanoTime();var out=new LinkedHashMap<String,Object>();
        out.put("chunk",List.of(cp.x,cp.z));out.put("denied",gate.denied());out.put("queriedNeighbours",gate.queried());
        out.put("hitKind",gate.kind());out.put("hitChunk",gate.neighbour()==null?List.of():List.of(gate.neighbour().x,gate.neighbour().z));
        out.put("decisionMs",(decision-start)/1e6);out.put("neighbourMs",(neighbour-decision)/1e6);out.put("builderMs",(end-neighbour)/1e6);out.put("totalMs",(end-start)/1e6);
        out.put("thread",Thread.currentThread().getName());return out;
    }
    private void checkBuilder(RuinDecision site,ChunkPos cp){
        var pieces=site.stub(context(site.kind(),cp)).getPiecesBuilder().build().pieces();
        var actual=pieces.stream().map(p->RuinDecision.Box.of(p.getBoundingBox())).toList();
        if(!actual.equals(site.boxes()))throw new IllegalStateException("Builder box mismatch at "+cp+" "+site.kind()+" "+site.placement()+": light="+site.boxes()+" builder="+actual);
    }
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"creek-light-neighbour-audit-worker"));
        try{executor.submit(()->{try{
            var audit=new CreekLightNeighbourAudit(server.overworld());report.put("seed",server.overworld().getSeed());
            var reference=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.lightNeighbourReference")))).getAsJsonObject();
            var group=reference.getAsJsonObject("groups").getAsJsonObject("grid/c");var expectedGate=group.getAsJsonObject("boundedRules").getAsJsonObject("R2");
            var all=group.getAsJsonObject("distribution").getAsJsonArray("rows");
            var first=new ArrayList<Map<String,Object>>();report.put("firstPass",first);
            var expectedDenials=new HashMap<Long,Boolean>();for(var v:expectedGate.getAsJsonArray("firstPassCosts")){var r=v.getAsJsonObject();expectedDenials.put(chunk(r).toLong(),r.get("deny").getAsBoolean());}
            for(var v:group.getAsJsonArray("rows")){
                var cp=chunk(v.getAsJsonObject());var row=audit.measure(cp,true);first.add(row);
                if(!Objects.equals(row.get("denied"),expectedDenials.get(cp.toLong())))throw new IllegalStateException("Light R2 gate changed "+cp);
                if(first.size()%10==0){write(report);System.out.println("LIGHT_R2 COST "+first.size());}
            }
            var warm=new ArrayList<Map<String,Object>>();report.put("warmPass",warm);
            for(var v:group.getAsJsonArray("rows")){var cp=chunk(v.getAsJsonObject());var row=audit.measure(cp,false);warm.add(row);if(!Objects.equals(row.get("denied"),expectedDenials.get(cp.toLong())))throw new IllegalStateException("Warm gate drift "+cp);}
            if(Boolean.getBoolean("orimod.lightNeighbourCostsOnly")){
                report.put("scope","costs-only; full distribution and four-rotation equivalence still required");
                report.put("status","complete");return;
            }
            var counts=new LinkedHashMap<String,Integer>();for(String k:List.of("creek_ruins","forest_spring","cave_altar","NONE"))counts.put(k,0);
            var forestReasons=new LinkedHashMap<String,Integer>();var selectedRows=new ArrayList<Map<String,Object>>();int n=0;
            report.put("counts",counts);report.put("forestFirstReasons",forestReasons);report.put("selectedRows",selectedRows);
            var fallback=new HashMap<Long,JsonObject>();for(var v:group.getAsJsonObject("yield").getAsJsonArray("creekDetails")){var r=v.getAsJsonObject();fallback.put(chunk(r).toLong(),r.getAsJsonObject("final"));}
            var actualCreeks=new ArrayList<ChunkPos>();
            for(var v:all){var r=v.getAsJsonObject();var cp=chunk(r);var site=audit.actual(cp);var kind=site.map(s->s.kind().id).orElse("NONE");counts.merge(kind,1,Integer::sum);
                var expected=r.getAsJsonObject("selected");if(expected.get("kind").getAsString().equals("creek_ruins")&&expectedDenials.get(cp.toLong()))expected=fallback.get(cp.toLong());
                if(!kind.equals(expected.get("kind").getAsString()))throw new IllegalStateException("Final kind mismatch "+cp+" expected="+expected+" actual="+kind);
                if(site.isPresent()){
                    var boxes=expected.getAsJsonArray("boxes");var actualBoxes=site.get().boxes();if(boxes.size()!=actualBoxes.size())throw new IllegalStateException("Box count "+cp);
                    for(int i=0;i<boxes.size();i++){var b=boxes.get(i).getAsJsonArray();var x=actualBoxes.get(i);if(!List.of(x.minX(),x.minY(),x.minZ(),x.maxX(),x.maxY(),x.maxZ()).equals(java.util.stream.IntStream.range(0,6).mapToObj(j->b.get(j).getAsInt()).toList()))throw new IllegalStateException("Reference final box mismatch "+cp);}
                }
                if(kind.equals("creek_ruins"))actualCreeks.add(cp);
                long forestStart=System.nanoTime();String forestReason=audit.base(RuinKind.FOREST,cp).reason();double forestMs=(System.nanoTime()-forestStart)/1e6;
                forestReasons.merge(forestReason,1,Integer::sum);
                selectedRows.add(Map.of("chunk",List.of(cp.x,cp.z),"kind",kind,"forestFirstReason",forestReason,"forestDecisionMs",forestMs));
                if(++n%250==0){write(report);System.out.println("LIGHT_R2 DISTRIBUTION "+n+" "+counts);}
            }
            if(!new Gson().toJsonTree(counts).equals(expectedGate.get("finalCounts")))throw new IllegalStateException("Final count changed "+counts);
            int collisions=0;var collisionRows=new ArrayList<Map<String,Object>>();
            for(var cp:actualCreeks){var site=audit.actual(cp).orElseThrow();boolean hit=false;
                for(var np:audit.neighbours(cp)){var neighbour=audit.actual(np);if(neighbour.isPresent()&&RuinSitingSites.intersects(site,neighbour.get(),false)){hit=true;collisionRows.add(Map.of("chunk",List.of(cp.x,cp.z),"neighbour",List.of(np.x,np.z)));}}
                if(hit)collisions++;
            }
            report.put("collisions",collisions);report.put("collisionRows",collisionRows);
            if(collisions!=expectedGate.get("finalCreekWriteBoxCollisions").getAsInt())throw new IllegalStateException("Collision count changed");
            var rotations=new LinkedHashMap<String,Integer>();int boxesChecked=0;
            // Full builders outside the measured gate, for every accepted kind
            // encountered in the entire grid and its actually queried neighbours.
            for(var e:new ArrayList<>(audit.checked.entrySet())){
                var cp=new ChunkPos(Long.parseLong(e.getKey().substring(e.getKey().indexOf(':')+1)));
                var original=e.getValue();var layout=original.kind().layout(original.version());
                var bounds=original.placement().blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1));
                for(var rotation:Rotation.values()){
                    var rotated=new RuinDecision(original.kind(),original.version(),
                            RuinPlacement.atMinimum(layout,new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ()),rotation),
                            original.approachHeights(),original.surfaceHeights());
                    audit.checkBuilder(rotated,cp);boxesChecked++;rotations.merge(rotation.name(),1,Integer::sum);
                }
                if(boxesChecked%100==0)System.out.println("LIGHT_R2 BUILDER_EQUIVALENCE "+boxesChecked);
            }
            report.put("builderSitesChecked",audit.checked.size());report.put("builderBoxesChecked",boxesChecked);report.put("rotations",rotations);if(rotations.size()!=4)throw new IllegalStateException("Missing rotation coverage");
            report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> r){try{Files.writeString(Path.of("light-neighbours.json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));}catch(Exception e){throw new RuntimeException(e);}}
}
