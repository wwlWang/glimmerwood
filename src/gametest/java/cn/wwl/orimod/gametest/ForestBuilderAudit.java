package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Gate 4: production findGenerationPoint AND actual builder, including all outer grid neighbours. */
final class ForestBuilderAudit {
    private final ForestSitingAudit world;
    private final Map<Long,Optional<Built>> selected = new LinkedHashMap<>();
    private final List<Map<String,Object>> boxDrifts = new ArrayList<>();
    private int builders;
    private record Built(RuinDecision decision, List<RuinDecision.Box> boxes, List<String> pieces, int terrainVersion) {}
    private static final Method FIND;
    static { try {
        FIND=RuinStructure.class.getDeclaredMethod("findGenerationPoint",Structure.GenerationContext.class);
        FIND.setAccessible(true);
    } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); } }
    private ForestBuilderAudit(ForestSitingAudit world) { this.world=world; }

    private List<RuinKind> order(ChunkPos cp) {
        var entries=new ArrayList<>(world.set.structures());
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(world.level.getSeed(),cp.x,cp.z);
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        var result=new ArrayList<RuinKind>();
        while(!entries.isEmpty()) {
            int roll=random.nextInt(total),index=0;
            for(var e:entries){roll-=e.weight();if(roll<0)break;index++;}
            var e=entries.remove(index);total-=e.weight();result.add(((RuinStructure)e.structure().value()).kind);
        }
        return List.copyOf(result);
    }
    @SuppressWarnings("unchecked")
    private Optional<Built> select(ChunkPos cp) {
        return selected.computeIfAbsent(cp.toLong(),unused->{
            for(var kind:order(cp)) {
                try {
                    var context=world.context(kind,cp);
                    var stub=(Optional<Structure.GenerationStub>)FIND.invoke(world.structure(kind),context);
                    if(stub.isEmpty())continue;
                    var decision=world.structure(kind).decision(world.context(kind,cp),kind.currentVersion()).site();
                    var pieces=stub.get().getPiecesBuilder().build().pieces();builders++;
                    var actual=pieces.stream().map(p->RuinDecision.Box.of(p.getBoundingBox())).toList();
                    if(!actual.equals(decision.boxes()))boxDrifts.add(Map.of("chunk",xy(cp),"expected",decision.boxes(),"actual",actual));
                    var main=(RuinPiece)pieces.stream().filter(RuinPiece.class::isInstance).findFirst().orElseThrow();
                    int terrain=main.createTag(StructurePieceSerializationContext.fromLevel(world.level)).getInt("ForestTerrainVersion");
                    if(kind==RuinKind.FOREST&&terrain!=1)throw new IllegalStateException("New forest missing terrain v1 "+cp);
                    return Optional.of(new Built(decision,actual,pieces.stream().map(p->p.getClass().getSimpleName()).toList(),terrain));
                } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            return Optional.empty();
        });
    }
    private static List<Integer> xy(ChunkPos cp){return List.of(cp.x,cp.z);}
    private static ChunkPos chunk(JsonObject row){var p=row.getAsJsonArray("chunk");return new ChunkPos(p.get(0).getAsInt(),p.get(1).getAsInt());}
    private Map<String,Object> row(ChunkPos cp,Optional<Built> value) {
        var out=new LinkedHashMap<String,Object>();out.put("chunk",xy(cp));out.put("first",order(cp).getFirst().id);
        out.put("kind",value.map(s->s.decision.kind().id).orElse("NONE"));
        value.ifPresent(s->{var p=s.decision.placement();out.put("rotation",p.rotation().name());
            out.put("origin",List.of(p.origin().getX(),p.origin().getY(),p.origin().getZ()));
            out.put("boxes",s.boxes);out.put("pieceTypes",s.pieces);out.put("forestTerrainVersion",s.terrainVersion);});
        return out;
    }
    private void run(JsonObject reference,Map<String,Object> report) {
        var points=new ArrayList<ChunkPos>();var drifts=new ArrayList<Map<String,Object>>();
        var counts=new TreeMap<String,Integer>();var transitions=new TreeMap<String,Integer>();var rows=new ArrayList<Map<String,Object>>();
        report.put("counts",counts);report.put("transitions",transitions);report.put("rows",rows);report.put("selectionDrifts",drifts);report.put("boxDrifts",boxDrifts);
        for(var value:reference.getAsJsonArray("rows")) {
            var old=value.getAsJsonObject();var cp=chunk(old);points.add(cp);
            if(!world.spread.getPotentialStructureChunk(world.level.getSeed(),cp.x,cp.z).equals(cp))throw new IllegalStateException("Not a real start "+cp);
            var built=select(cp);var r=row(cp,built);rows.add(r);String kind=(String)r.get("kind");
            counts.merge(kind,1,Integer::sum);transitions.merge(r.get("first")+" -> "+kind,1,Integer::sum);
            if(!kind.equals(old.get("kind").getAsString())||built.isPresent()&&!built.get().decision.placement().rotation().name().equals(old.get("rotation").getAsString()))
                drifts.add(Map.of("chunk",xy(cp),"reference",old,"current",r));
            if(rows.size()%100==0){write(report);System.out.println("FOREST_BUILDER grid="+rows.size()+" counts="+counts);}
        }
        if(points.size()!=2025)throw new IllegalStateException("Wrong denominator");
        // Expand the entire 45 x 45 region grid, not just neighbours of accepted starts.
        var grid=new HashSet<Long>();points.forEach(p->grid.add(p.toLong()));var outer=new TreeMap<Long,ChunkPos>();
        for(var p:points)for(var q:world.neighbours(p))if(!grid.contains(q.toLong()))outer.put(q.toLong(),q);
        var outerRows=new ArrayList<Map<String,Object>>();for(var cp:outer.values())outerRows.add(row(cp,select(cp)));
        report.put("outerRows",outerRows);report.put("outerStarts",outer.size());report.put("actualBuilders",builders);
        var pairs=new TreeMap<String,Map<String,Object>>();
        for(var cp:points){var own=select(cp);if(own.isEmpty())continue;
            for(var np:world.neighbours(cp)){var other=select(np);if(other.isEmpty())continue;
                var overlaps=new ArrayList<Map<String,Object>>();
                for(int i=0;i<own.get().boxes.size();i++)for(int j=0;j<other.get().boxes.size();j++) {
                    var a=own.get().boxes.get(i);var b=other.get().boxes.get(j);if(!a.intersects(b,false))continue;
                    overlaps.add(Map.of("ownPiece",i,"otherPiece",j,"intersection",new RuinDecision.Box(Math.max(a.minX(),b.minX()),Math.max(a.minY(),b.minY()),Math.max(a.minZ(),b.minZ()),Math.min(a.maxX(),b.maxX()),Math.min(a.maxY(),b.maxY()),Math.min(a.maxZ(),b.maxZ()))));
                }
                if(overlaps.isEmpty())continue;
                long a=cp.toLong(),b=np.toLong();pairs.putIfAbsent(Math.min(a,b)+":"+Math.max(a,b),Map.of("own",row(cp,own),"other",row(np,other),"components",overlaps));
            }
        }
        report.put("intersectionPairs",pairs.values());report.put("allPairCount",pairs.size());
        long forest=pairs.values().stream().filter(p->involves(p,"forest_spring")).count(),creek=pairs.values().stream().filter(p->involves(p,"creek_ruins")).count();
        report.put("forestPairCount",forest);report.put("creekPairCount",creek);
        boolean known=pairs.values().stream().allMatch(p->{var a=(Map<?,?>)p.get("own");var b=(Map<?,?>)p.get("other");return Set.of(a.get("chunk"),b.get("chunk")).equals(Set.of(List.of(-84,-94),List.of(-82,-90)));});
        // Approved external qualifications change the selected distribution; the grid itself and
        // every row are still compared to the approved diagnostic reference above.
        int total=counts.getOrDefault("forest_spring",0)+counts.getOrDefault("creek_ruins",0)+counts.getOrDefault("cave_altar",0);
        boolean pass=drifts.isEmpty()&&boxDrifts.isEmpty()&&counts.getOrDefault("forest_spring",0)==20&&counts.getOrDefault("creek_ruins",0)==38&&counts.getOrDefault("cave_altar",0)==33
                &&total>=78&&total<=130&&forest==0&&creek==0&&pairs.size()<=1&&known;
        report.put("acceptancePassed",pass);report.put("status",pass?"complete":"threshold_failed");
    }
    private static boolean involves(Map<String,Object> p,String kind){return ((Map<?,?>)p.get("own")).get("kind").equals(kind)||((Map<?,?>)p.get("other")).get("kind").equals(kind);}
    static void started(ServerStartedEvent event) {
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");report.put("seed",server.overworld().getSeed());
        report.put("scope","2025 real grid starts + full outer ring; production findGenerationPoint and actual builder; actual main/rim and approach 3D boxes; no block placement");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"forest-builder-worker"));long start=System.nanoTime();
        try {executor.submit(()->{try {
            var ref=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.forestBuilderReference")))).getAsJsonObject();
            if(ref.get("seed").getAsLong()!=server.overworld().getSeed())throw new IllegalStateException("Seed drift");
            new ForestBuilderAudit(new ForestSitingAudit(server.overworld())).run(ref,report);
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();report.put("wallMs",(System.nanoTime()-start)/1e6);write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> report){try{Files.writeString(Path.of("forest-builder.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
}
