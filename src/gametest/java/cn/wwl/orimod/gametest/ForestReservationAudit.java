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

/** Compare the reservation proposal with actual production decisions; never invoke pieces builders. */
final class ForestReservationAudit {
    private final ForestSitingAudit a;
    private final boolean reservations;
    private final ForestReservationProbe reservationProbe;
    private boolean production;
    private Map<String,RuinStructure.SiteResult> rawDecisions;
    // Separate policy namespace, immutable values, production cache capacities.
    private final RuinAuditCache<String,RuinStructure.SiteResult> bases=new RuinAuditCache<>(4096);
    private final RuinAuditCache<Long,Optional<RuinDecision>> predictions=new RuinAuditCache<>(2048);
    private final RuinAuditCache<Long,List<Candidate>> plans=new RuinAuditCache<>(2048);
    private final Map<Long,Optional<RuinDecision>> selected=new LinkedHashMap<>();
    private final Map<String,RuinStructure.SiteResult> evidence=new LinkedHashMap<>();
    private final List<Map<String,Object>> costs=new ArrayList<>();
    private final List<Trial> trials=new ArrayList<>();
    private record Candidate(RuinKind kind,List<RuinDecision.Box> boxes){}
    private record Trial(ChunkPos chunk,RuinDecision own,boolean denied){}

    private ForestReservationAudit(ForestSitingAudit a,boolean reservations){this.a=a;this.reservations=reservations;this.reservationProbe=new ForestReservationProbe(a.level);}
    private ForestReservationAudit(ForestSitingAudit a,ForestExternalTruth truth,boolean rotationOnly,Map<String,RuinStructure.SiteResult> raw){
        this.a=a;this.reservations=true;this.reservationProbe=new ForestReservationProbe(a.level,truth,rotationOnly);this.rawDecisions=raw;
    }
    private ForestReservationAudit(ForestSitingAudit a,ForestCheapExternal cheap){this.a=a;this.reservations=true;this.reservationProbe=new ForestReservationProbe(a.level,cheap);}
    private RuinStructure.SiteResult raw(RuinKind kind,ChunkPos cp){
        if(rawDecisions==null)return a.structure(kind).decision(a.context(kind,cp),1);
        return rawDecisions.computeIfAbsent(kind.id+":"+cp.toLong(),unused->a.structure(kind).decision(a.context(kind,cp),1));
    }
    private RuinStructure.SiteResult forest(ChunkPos cp){
        var c=a.context(RuinKind.FOREST,cp);var result=raw(RuinKind.FOREST,cp);
        if(result.site()!=null&&reservations){var rejection=reservationProbe.reject(c,result.site());if(rejection!=null)return fail(rejection);}
        return result;
    }
    private static RuinStructure.SiteResult fail(String why){return new RuinStructure.SiteResult(null,why);}
    private RuinStructure.SiteResult base(RuinKind kind,ChunkPos cp){return bases.get(kind.id+":"+cp.toLong(),()->{
        var result=kind==RuinKind.FOREST?forest(cp):raw(kind,cp);
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
        long start=System.nanoTime();boolean deny=production?RuinSitingSites.yield(a.context(own.kind(),cp),own).denied():denied(cp,own,true);long elapsed=System.nanoTime()-start;
        costs.add(Map.of("chunk",List.of(cp.x,cp.z),"kind",own.kind().id,"pass",pass,"denied",deny,"ms",elapsed/1e6));
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
        report.put("externalAfterGrid",reservationProbe.externalReport());
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
        report.put("reservationProbe",reservationProbe.report());report.put("sitingBuilderCalls",0);
        report.put("timingScope",production?"actual production R2 after own cached decision; excludes own site and builder, not whole-start acceptance":"diagnostic: cached production terrain decision precedes proposed reservation; not production latency acceptance");
        return report;
    }
    private static ChunkPos chunk(JsonObject row){var c=row.getAsJsonArray("chunk");return new ChunkPos(c.get(0).getAsInt(),c.get(1).getAsInt());}
    static void cheapStarted(ServerStartedEvent event){
        if(Boolean.getBoolean("orimod.forestRecordExternalStarts")){recordExternalStarts(event);return;}
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");report.put("seed",server.overworld().getSeed());
        report.put("scope","Cheap qualification, no neighbour pieces builders; 2025 grid + full weighted fallback/R2; production unchanged");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"forest-cheap-external-worker"));
        try{executor.submit(()->{try{
            var reference=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.forestExternalTruthReference")))).getAsJsonObject();
            if(reference.get("seed").getAsLong()!=server.overworld().getSeed())throw new IllegalStateException("Seed mismatch");
            var a=new ForestSitingAudit(server.overworld());var cheap=new ForestCheapExternal(a.level);
            var points=new ArrayList<ChunkPos>();for(var row:reference.getAsJsonObject("allActualExternal").getAsJsonArray("rows"))points.add(chunk(row.getAsJsonObject()));
            if(points.size()!=2025)throw new IllegalStateException("Wrong denominator");
            var result=new LinkedHashMap<String,Object>();report.put("cheap",result);
            var engine=Boolean.getBoolean("orimod.forestReservationProduction")?new ForestReservationAudit(a,false):new ForestReservationAudit(a,cheap);
            engine.production=Boolean.getBoolean("orimod.forestReservationProduction");
            report.put("productionEntry",engine.production);
            if(engine.production)report.put("scope","Actual production base decisions and one-layer R2; 2025 weighted/fallback grid, compared pointwise with approved diagnostic proposal; no builders");
            engine.run(points,result);report.put("external",cheap.report());
            String previous=System.getProperty("orimod.forestCheapReference");
            if(previous!=null){
                var old=JsonParser.parseString(Files.readString(Path.of(previous))).getAsJsonObject().getAsJsonObject("cheap");
                for(String field:List.of("rows","counts","transitions","intersectionPairs"))
                    if(!old.get(field).equals(new Gson().toJsonTree(result.get(field))))throw new AssertionError("Geometry prefilter drift: "+field);
                report.put("preFilterReference",Map.of("rows",2025,"equal",true,"fields",List.of("rows","counts","transitions","intersectionPairs")));
            }
            report.put("warmExternalProbe",cheap.warmCacheProbe());
            // After timing: compare eligibility and glades lightweight boxes with archived full vanilla builders.
            var comparisons=new ArrayList<Map<String,Object>>();report.put("qualificationComparisons",comparisons);
            for(var external:reference.getAsJsonObject("externalTruth").getAsJsonArray("starts")){
                var e=external.getAsJsonObject();var id=net.minecraft.resources.ResourceLocation.parse(e.get("set").getAsString());var cp=chunk(e);
                var holder=a.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET)
                        .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,id));
                var d=cheap.lookup(holder,cp);if(d.valid()!=e.get("valid").getAsBoolean())throw new AssertionError("Full qualification mismatch "+cp);
                if(d.valid()&&!d.selected().equals(e.get("selected").getAsString()))throw new AssertionError("Full winner mismatch "+cp);
                if(d.valid()&&id.getPath().equals("glades_outpost")){
                    var full=e.getAsJsonArray("trials").asList().stream().map(JsonElement::getAsJsonObject).filter(t->t.has("pieces")).findFirst().orElseThrow().getAsJsonArray("pieces");
                    if(full.size()!=d.boxes().size())throw new AssertionError("Glades box count");
                    for(int i=0;i<full.size();i++)if(!full.get(i).getAsJsonObject().get("box").equals(new Gson().toJsonTree(ForestExternalTruth.box(d.boxes().get(i).mutable()))))throw new AssertionError("Glades lightweight box mismatch");
                }
                comparisons.add(Map.of("set",id.toString(),"chunk",List.of(cp.x,cp.z),"equal",true));
            }
            var registry=a.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
            var glades=registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,net.minecraft.resources.ResourceLocation.parse("orimod:glades_outpost")));
            var cp=new ChunkPos(13,-59);report.put("concurrency",ForestCheapExternal.concurrency(a.level,glades,cp,cheap.lookup(glades,cp)));
            // Pillager path has no hit in the grid: exercise its same vanilla qualification separately, outside timing.
            var pillager=registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,net.minecraft.resources.ResourceLocation.withDefaultNamespace("pillager_outposts")));
            var spread=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)pillager.value().placement();
            var pillagerChunk=spread.getPotentialStructureChunk(a.level.getSeed(),0,0);
            var extra=new ForestCheapExternal(a.level);var decision=extra.lookup(pillager,pillagerChunk);
            report.put("pillagerSeparateQualification",Map.of("chunk",List.of(pillagerChunk.x,pillagerChunk.z),"decision",decision,"probe",extra.report(),"warm",extra.warmCacheProbe(),"scope","separate potential-grid qualification only; not claimed as a density hit"));
            report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    /** Save actual vanilla-selected starts/absence at FULL, not an absence inferred from an unloaded chunk. */
    private static void recordExternalStarts(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();var report=new LinkedHashMap<String,Object>();
        var rows=new ArrayList<Map<String,Object>>();report.put("rows",rows);report.put("seed",level.getSeed());
        try{
            if(level.getSeed()!=116118L)throw new IllegalStateException("Qualification archive seed");
            var context=net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level);
            for(var cp:List.of(new ChunkPos(-118,-112),new ChunkPos(83,-17),new ChunkPos(109,53),new ChunkPos(77,81))){
                var owned=new ArrayList<ChunkPos>();
                try{
                    level.setChunkForced(cp.x,cp.z,true);owned.add(cp);
                    var chunk=level.getChunk(cp.x,cp.z);var starts=new TreeMap<String,String>();
                    for(var entry:chunk.getAllStarts().entrySet())starts.put(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .getKey(entry.getKey()).toString(),entry.getValue().createTag(context,cp).toString());
                    rows.add(Map.of("chunk",List.of(cp.x,cp.z),"status",chunk.getPersistedStatus().getName(),"starts",starts));
                    System.out.println("FOREST_EXTERNAL_SAVED_START "+cp+" "+starts.keySet());
                }finally{CreekFixtureChunks.release(level,owned);}
            }
            report.put("status","complete");
        }catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{write(report);server.halt(false);}
    }
    static void truthStarted(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");report.put("seed",server.overworld().getSeed());
        report.put("scope","Eight former winners + full 2025 weighted/fallback/R2 recomputation; diagnostic only, shared raw decision memoization, NOT latency acceptance");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"forest-external-truth-worker"));
        try{executor.submit(()->{try{
            var archived=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.forestReservationReference")))).getAsJsonObject();
            if(archived.get("seed").getAsLong()!=server.overworld().getSeed())throw new IllegalStateException("Seed mismatch");
            var a=new ForestSitingAudit(server.overworld());var truth=new ForestExternalTruth(a.level);
            var raw=new LinkedHashMap<String,RuinStructure.SiteResult>();
            var cases=new ArrayList<Map<String,Object>>();report.put("cases",cases);
            var old=archived.getAsJsonObject("baseline").getAsJsonArray("rows");var proposed=archived.getAsJsonObject("proposal").getAsJsonArray("rows");
            var hits=archived.getAsJsonObject("proposal").getAsJsonObject("reservationProbe").getAsJsonArray("hits");
            var points=new ArrayList<ChunkPos>();
            for(int i=0;i<old.size();i++){
                var row=old.get(i).getAsJsonObject();var cp=chunk(row);points.add(cp);
                if(row.get("kind").equals(proposed.get(i).getAsJsonObject().get("kind")))continue;
                var own=a.structure(RuinKind.FOREST).decision(a.context(RuinKind.FOREST,cp),1);
                if(own.site()==null)throw new IllegalStateException("Former forest not viable "+cp);
                raw.put(RuinKind.FOREST.id+":"+cp.toLong(),own);
                var hit=hits.asList().stream().map(JsonElement::getAsJsonObject).filter(h->h.get("forestChunk").equals(row.get("chunk"))).findFirst().orElseThrow();
                String reason=hit.get("reason").getAsString();String id=reason.equals("STRUCTURE_GLADES_OUTPOST")?"orimod:glades_outpost":"minecraft:"+reason.substring("STRUCTURE_".length()).toLowerCase(Locale.ROOT);
                var xy=hit.getAsJsonArray("candidateStart");var other=new ChunkPos(xy.get(0).getAsInt(),xy.get(1).getAsInt());
                var holder=a.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET)
                        .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,net.minecraft.resources.ResourceLocation.parse(id)));
                var result=truth.comparison(holder,other,own.site());result.put("forestChunk",List.of(cp.x,cp.z));result.put("previousReason",reason);cases.add(result);
                report.put("externalTruth",truth.report());write(report);System.out.println("FOREST_EXTERNAL_CASE "+cp+" "+result.get("classification"));
            }
            if(points.size()!=2025||cases.size()!=8)throw new IllegalStateException("Wrong audit denominator");
            var conservative=new LinkedHashMap<String,Object>();report.put("conservativeRecheck",conservative);
            var c=new ForestReservationAudit(a,true);c.rawDecisions=raw;c.run(points,conservative);
            var rows=(List<Map<String,Object>>)conservative.get("rows");
            for(int i=0;i<points.size();i++)if(!rows.get(i).get("kind").equals(proposed.get(i).getAsJsonObject().get("kind").getAsString()))throw new IllegalStateException("Conservative archive drift "+points.get(i));
            report.put("conservativeArchiveComparisons",points.size());
            var rotation=new LinkedHashMap<String,Object>();report.put("actualVillage_rotatedGlades",rotation);
            new ForestReservationAudit(a,truth,true,raw).run(points,rotation);
            var exact=new LinkedHashMap<String,Object>();report.put("allActualExternal",exact);
            new ForestReservationAudit(a,truth,false,raw).run(points,exact);
            report.put("externalTruth",truth.report());report.put("rawDecisionCount",raw.size());report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");report.put("seed",server.overworld().getSeed());
        report.put("scope","2025 real grid, proposed forest external reservations + one-layer forest/creek R2; production unchanged");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"forest-reservation-worker"));
        try{executor.submit(()->{try{
            var reference=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.forestBuilderReference")))).getAsJsonObject();
            if(reference.get("seed").getAsLong()!=server.overworld().getSeed())throw new IllegalStateException("Seed mismatch");
            var a=new ForestSitingAudit(server.overworld());var points=new ArrayList<ChunkPos>();
            for(var v:reference.getAsJsonArray("rows")){var cp=chunk(v.getAsJsonObject());
                if(!a.spread.getPotentialStructureChunk(a.level.getSeed(),cp.x,cp.z).equals(cp))throw new IllegalStateException("Non-grid start");points.add(cp);}
            if(points.size()!=2025)throw new IllegalStateException("Wrong denominator");
            var before=new LinkedHashMap<String,Object>();report.put("baseline",before);new ForestReservationAudit(a,false).run(points,before);
            var baselineRows=(List<Map<String,Object>>)before.get("rows");
            for(int i=0;i<points.size();i++)if(!baselineRows.get(i).get("kind").equals(reference.getAsJsonArray("rows").get(i).getAsJsonObject().get("kind").getAsString()))
                throw new IllegalStateException("Archived selected kind drift "+points.get(i));
            report.put("referenceComparisons",points.size());
            var proposed=new LinkedHashMap<String,Object>();report.put("proposal",proposed);new ForestReservationAudit(a,true).run(points,proposed);
            report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> report){try{Files.writeString(ReportFiles.output(Path.of(System.getProperty("orimod.forestReservationOutput","forest-reservation.json"))),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
}
