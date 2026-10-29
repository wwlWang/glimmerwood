package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.*;
import java.util.*;

/** Gate 1b only: forest + creek one-layer R2, never wired to a production builder. */
final class ForestR2Audit {
    private final ForestSitingAudit a;
    // Separate policy namespace, immutable values, production cache capacities.
    private final RuinAuditCache<String,RuinStructure.SiteResult> bases=new RuinAuditCache<>(4096);
    private final RuinAuditCache<Long,Optional<RuinDecision>> predictions=new RuinAuditCache<>(2048);
    private final RuinAuditCache<Long,List<Candidate>> plans=new RuinAuditCache<>(2048);
    private final Map<Long,Optional<RuinDecision>> selected=new LinkedHashMap<>();
    private final Map<String,RuinStructure.SiteResult> evidence=new LinkedHashMap<>();
    private final Map<Long,JsonObject> archivedForest=new HashMap<>();
    private final List<Map<String,Object>> costs=new ArrayList<>();
    private final List<Trial> trials=new ArrayList<>();
    private record Candidate(RuinKind kind,List<RuinDecision.Box> boxes){}
    private record Trial(ChunkPos chunk,RuinDecision own,boolean denied){}
    private long heightQueries;
    private int forestArchiveChecks;

    private ForestR2Audit(ForestSitingAudit a,JsonObject reference){this.a=a;
        for(var value:reference.getAsJsonArray("forestSamples")){var row=value.getAsJsonObject();archivedForest.put(chunk(row).toLong(),row);}}

    /** Same accepted set as exhaustive ForestSiteRules.inspect, with monotone early rejection. */
    private RuinStructure.SiteResult forest(ChunkPos cp){
        var rejected=a.cheap(a.context(RuinKind.FOREST,cp));if(!rejected.isEmpty())return fail(rejected.getFirst());
        var c=a.context(RuinKind.FOREST,cp);var layout=RuinKind.FOREST.layout(1);
        var footprint=RuinPlacement.atMinimum(layout,cp.getWorldPosition(),Rotation.getRandom(c.random()));
        var h=new ForestSitingAudit.Heights(c);int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE,n=0;int[] core=new int[ForestSiteRules.CORE.size()];
        for(var p:ForestSiteRules.CORE){int y=height(h,footprint.block(new BlockPos(p.x(),0,p.z())));core[n++]=y;lo=Math.min(lo,y);hi=Math.max(hi,y);
            if(!RuinSiteRules.surfaceInRange(y))return fail(y<63?"HEIGHT_LOW":"HEIGHT_HIGH");
            if(hi-lo>ForestSiteRules.CORE_RELIEF)return fail("CORE_RELIEF");}
        int coreLo=lo,coreHi=hi;Arrays.sort(core);int anchor=core[core.length/2];
        for(var p:ForestSiteRules.OUTER){int y=height(h,footprint.block(new BlockPos(p.x(),0,p.z())));lo=Math.min(lo,y);hi=Math.max(hi,y);
            if(!RuinSiteRules.surfaceInRange(y))return fail(y<63?"HEIGHT_LOW":"HEIGHT_HIGH");
            if(hi-lo>ForestSiteRules.OUTER_RELIEF)return fail("OUTER_RELIEF");}
        var door=RuinApproachPiece.doorway(RuinKind.FOREST,layout,footprint);
        for(int dx=-2;dx<=2;dx++){int y=height(h,door.block(new BlockPos(dx,0,-1)));coreLo=Math.min(coreLo,y);coreHi=Math.max(coreHi,y);
            if(!RuinSiteRules.footprintOk(coreLo,coreHi))return fail("ENTRY");}
        var placed=new RuinPlacement(footprint.origin().atY(anchor-layout.surfaceY()),footprint.rotation());
        var doorway=RuinApproachPiece.doorway(RuinKind.FOREST,layout,placed);var approach=new ArrayList<Integer>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-9;dz<=-1;dz++){
            int y=height(h,doorway.block(new BlockPos(dx,0,dz)));approach.add(y);
            if(!RuinSiteRules.approachOk(y,doorway.origin().getY()))return fail("APPROACH");}
        var entry=BlockPos.containing(placed.point(layout.entry()));
        if(!c.validBiome().test(c.biomeSource().getNoiseBiome(entry.getX()>>2,entry.getY()>>2,entry.getZ()>>2,c.randomState().sampler())))return fail("BIOME_ENTRY");
        if(!h.dry())return fail("WATER");
        if(!RuinSiteRules.fitsWorld(placed.origin().getY(),layout.size().getY(),a.level.getMinBuildHeight(),a.level.getMaxBuildHeight()))return fail("BUILD_HEIGHT");
        var site=new RuinDecision(RuinKind.FOREST,1,placed,approach,Map.of());
        if(!GinsoGenerationGuard.checkBounds(c.randomState(),site.boxes().getFirst().mutable()).allowed())return fail("GENERATED");
        return new RuinStructure.SiteResult(site,"PASS");
    }
    private int height(ForestSitingAudit.Heights h,BlockPos p){heightQueries++;return h.at(p);}
    private static RuinStructure.SiteResult fail(String why){return new RuinStructure.SiteResult(null,why);}
    private RuinStructure.SiteResult base(RuinKind kind,ChunkPos cp){return bases.get(kind.id+":"+cp.toLong(),()->{
        var result=kind==RuinKind.FOREST?forest(cp):a.structure(kind).decision(a.context(kind,cp),1);
        evidence.put(kind.id+":"+cp.toLong(),result);return result;
    });}
    private List<Candidate> plan(ChunkPos cp){return plans.get(cp.toLong(),()->{
        var entries=new ArrayList<>(a.set.structures());var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(a.level.getSeed(),cp.x,cp.z);
        var rotation=Rotation.getRandom(a.context(RuinKind.FOREST,cp).random());var out=new ArrayList<Candidate>();
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while(!entries.isEmpty()){
            int roll=random.nextInt(total),i=0;for(var e:entries){roll-=e.weight();if(roll<0)break;i++;}
            var e=entries.remove(i);total-=e.weight();var kind=((RuinStructure)e.structure().value()).kind;
            var p=RuinPlacement.atMinimum(kind.layout(1),cp.getWorldPosition(),rotation);
            out.add(new Candidate(kind,RuinDecision.boxes(kind,1,p)));
        }return List.copyOf(out);
    });}
    private Optional<RuinDecision> predicted(ChunkPos cp){return predictions.get(cp.toLong(),()->{
        for(var c:plan(cp)){var found=base(c.kind,cp);if(found.site()!=null)return Optional.of(found.site());}return Optional.empty();
    });}
    private Optional<RuinDecision> collision(ChunkPos cp,RuinDecision own){
        var known=predictions.completed(cp.toLong());if(known!=null)return known;
        var order=plan(cp);boolean[] relevant=new boolean[order.size()];
        for(int i=0;i<order.size();i++)for(var x:own.boxes())for(var y:order.get(i).boxes)relevant[i]|=x.intersects(y,true);
        var found=new ArrayList<Optional<RuinDecision>>(Collections.nCopies(order.size(),null));
        int winner=RuinAuditCache.relevantWinner(relevant,i->{var r=Optional.ofNullable(base(order.get(i).kind,cp).site());found.set(i,r);return r.isPresent();});
        if(winner<0)return Optional.empty(); // Not a cached NONE: irrelevant viable kinds may remain unknown.
        var result=found.get(winner);predictions.remember(cp.toLong(),result);return result;
    }
    private boolean denied(ChunkPos cp,RuinDecision own,boolean optimized){
        for(var np:a.neighbours(cp)){
            var other=optimized?collision(np,own):predicted(np); // No neighbour yield, no recursive closure.
            if(other.isPresent()&&RuinSitingSites.intersects(own,other.get(),true))return true;
        }return false;
    }
    private boolean measure(ChunkPos cp,RuinDecision own,String pass){
        long start=System.nanoTime(),q=heightQueries;boolean deny=denied(cp,own,true);long elapsed=System.nanoTime()-start;
        costs.add(Map.of("chunk",List.of(cp.x,cp.z),"kind",own.kind().id,"pass",pass,"denied",deny,"ms",elapsed/1e6,"forestHeightCalls",heightQueries-q));
        if(pass.equals("first"))trials.add(new Trial(cp,own,deny));return deny;
    }
    private Optional<RuinDecision> selected(ChunkPos cp){return selected.computeIfAbsent(cp.toLong(),unused->{
        for(var c:plan(cp)){var r=base(c.kind,cp);if(r.site()==null)continue;
            if(c.kind!=RuinKind.CAVE&&measure(cp,r.site(),"first"))continue;return Optional.of(r.site());}
        return Optional.empty();
    });}
    private Map<String,Object> stats(){return Map.of("base",bases.stats(),"prediction",predictions.stats(),"plan",plans.stats());}
    private Map<String,Object> run(List<ChunkPos> points,Map<String,Object> report){
        var counts=new LinkedHashMap<String,Integer>();for(String k:List.of("forest_spring","creek_ruins","cave_altar","NONE"))counts.put(k,0);
        var transitions=new TreeMap<String,Integer>();var rows=new ArrayList<Map<String,Object>>();
        report.put("counts",counts);report.put("transitions",transitions);report.put("rows",rows);report.put("r2Costs",costs);
        long start=System.nanoTime();
        for(var cp:points){var site=selected(cp);String kind=site.map(s->s.kind().id).orElse("NONE"),first=plan(cp).getFirst().kind.id;
            counts.merge(kind,1,Integer::sum);transitions.merge(first+" -> "+kind,1,Integer::sum);
            var row=new LinkedHashMap<String,Object>();row.put("chunk",List.of(cp.x,cp.z));row.put("first",first);row.put("kind",kind);
            site.ifPresent(s->{row.put("rotation",s.placement().rotation().name());row.put("boxes",ForestSitingAudit.boxes(s));});rows.add(row);
            if(rows.size()%100==0){write(report);System.out.println("FOREST_R2 "+rows.size()+" "+counts);}}
        report.put("gridSelectionMs",(System.nanoTime()-start)/1e6);report.put("cacheAfterGrid",stats());report.put("gridTrialCount",trials.size());
        var gridTrials=List.copyOf(trials);
        for(var t:gridTrials)if(measure(t.chunk,t.own,"warm")!=t.denied)throw new IllegalStateException("Warm R2 drift "+t.chunk);
        report.put("cacheAfterWarm",stats());
        // Outside the measured passes: complete, unpruned reference and archive checks.
        for(var t:gridTrials)if(denied(t.chunk,t.own,false)!=t.denied)throw new IllegalStateException("Pruned R2 drift "+t.chunk);
        report.put("unprunedR2Comparisons",gridTrials.size());
        var pairs=new TreeMap<String,Map<String,Object>>();
        for(var cp:points){var own=selected(cp);if(own.isEmpty())continue;
            for(var np:a.neighbours(cp)){var other=selected(np);if(other.isEmpty()||!RuinSitingSites.intersects(own.get(),other.get(),false))continue;
                long x=cp.toLong(),y=np.toLong();pairs.putIfAbsent(Math.min(x,y)+":"+Math.max(x,y),Map.of("chunk",List.of(cp.x,cp.z),"kind",own.get().kind().id,"boxes",ForestSitingAudit.boxes(own.get()),
                        "neighbour",List.of(np.x,np.z),"neighbourKind",other.get().kind().id,"neighbourBoxes",ForestSitingAudit.boxes(other.get())));}}
        report.put("intersectionPairs",List.copyOf(pairs.values()));report.put("allPairCount",pairs.size());
        for(var kind:List.of(RuinKind.FOREST,RuinKind.CREEK))report.put(kind.id+"PairCount",pairs.values().stream().filter(p->p.get("kind").equals(kind.id)||p.get("neighbourKind").equals(kind.id)).count());
        for(var cp:points){var r=base(RuinKind.FOREST,cp);var old=archivedForest.get(cp.toLong());boolean expected=old.get("firstReason").getAsString().equals("PASS");
            if(expected!=(r.site()!=null))throw new IllegalStateException("Early-exit acceptance drift "+cp+" expected "+old+" actual "+r);
            if(expected){var p=r.site().placement().origin();if(!new Gson().toJsonTree(List.of(p.getX(),p.getY(),p.getZ())).equals(old.get("origin")))throw new IllegalStateException("Anchor drift "+cp);}
            forestArchiveChecks++;}
        report.put("exhaustiveForestArchiveComparisons",forestArchiveChecks);
        var cave=new ArrayList<Map<String,Object>>();
        for(var cp:List.of(new ChunkPos(-84,-94),new ChunkPos(-82,-90))){
            var site=base(RuinKind.CAVE,cp).site();if(site==null)throw new IllegalStateException("Missing cave pair member");
            var pieces=site.stub(a.context(RuinKind.CAVE,cp)).getPiecesBuilder().build().pieces();
            var actual=pieces.stream().map(p->RuinDecision.Box.of(p.getBoundingBox())).toList();
            if(!actual.equals(site.boxes()))throw new IllegalStateException("Cave actual builder box differs");
            cave.add(Map.of("chunk",List.of(cp.x,cp.z),"origin",List.of(site.placement().origin().getX(),site.placement().origin().getY(),site.placement().origin().getZ()),"rotation",site.placement().rotation().name(),"boxes",ForestSitingAudit.boxes(site),"actualBuilderMatches",true));}
        report.put("cavePair",cave);report.put("caveInvestigationBuilderCalls",2);report.put("sitingBuilderCalls",0);
        return report;
    }
    private static ChunkPos chunk(JsonObject row){var c=row.getAsJsonArray("chunk");return new ChunkPos(c.get(0).getAsInt(),c.get(1).getAsInt());}
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");report.put("seed",server.overworld().getSeed());
        report.put("scope","diagnostic forest/creek one-layer R2; no production integration; costs precede exhaustive reference verification");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"forest-stage-one-b-worker"));
        try{executor.submit(()->{try{
            var reference=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.forestStageOneReference")))).getAsJsonObject();
            if(reference.get("seed").getAsLong()!=server.overworld().getSeed())throw new IllegalStateException("Seed mismatch");
            var a=new ForestSitingAudit(server.overworld());var audit=new ForestR2Audit(a,reference);var points=new ArrayList<ChunkPos>();
            for(var v:reference.getAsJsonObject("proposal").getAsJsonArray("rows")){var cp=chunk(v.getAsJsonObject());
                if(!a.spread.getPotentialStructureChunk(a.level.getSeed(),cp.x,cp.z).equals(cp))throw new IllegalStateException("Non-grid start");points.add(cp);}
            if(points.size()!=2025)throw new IllegalStateException("Wrong denominator");audit.run(points,report);report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> report){try{Files.writeString(Path.of("forest-stage-one-b.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
}
