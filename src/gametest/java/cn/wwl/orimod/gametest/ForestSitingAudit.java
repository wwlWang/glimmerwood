package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Stage one only. Separate policy caches, exact production terrain sampler, no builders or writes.
 * The reference policy must reproduce every archived final kind before the proposal is evaluated. */
final class ForestSitingAudit {
    private static final String POLICY="forest_core4_outer8_median_terrain1";
    final ServerLevel level;
    final StructureSet set;
    final RandomSpreadStructurePlacement spread;
    private final Map<Long,ForestSample> samples=new LinkedHashMap<>();
    private record ForestSample(RuinStructure.SiteResult result,Map<String,Object> data) {}
    ForestSitingAudit(ServerLevel level){
        this.level=level;
        set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins"));
        spread=(RandomSpreadStructurePlacement)set.placement();
    }
    RuinStructure structure(RuinKind kind){return (RuinStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());}
    Structure.GenerationContext context(RuinKind kind,ChunkPos cp){
        var g=level.getChunkSource().getGenerator();return new Structure.GenerationContext(level.registryAccess(),g,g.getBiomeSource(),level.getChunkSource().randomState(),
                level.getStructureManager(),level.getSeed(),cp,level,structure(kind).biomes()::contains);
    }
    /** Reflection is confined to gametest: reuse the exact floor/dry implementation without a production hook. */
    static final class Heights {
        private static final Constructor<?> CTOR;
        private static final Method HEIGHT,DRY;
        static {try{
            var c=Class.forName("cn.wwl.orimod.world.RuinStructure$TerrainSamples");
            CTOR=c.getDeclaredConstructors()[0];CTOR.setAccessible(true);
            HEIGHT=c.getDeclaredMethod("height",BlockPos.class);DRY=c.getDeclaredMethod("dry");HEIGHT.setAccessible(true);DRY.setAccessible(true);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}}
        private final Object sampler;private final Set<Long> queried=new HashSet<>();
        Heights(Structure.GenerationContext c){try{sampler=CTOR.newInstance(c,false,true,null);}catch(Exception e){throw new IllegalStateException(e);}}
        int at(BlockPos p){try{queried.add(p.atY(0).asLong());return (int)HEIGHT.invoke(sampler,p);}catch(Exception e){throw new IllegalStateException(e);}}
        boolean dry(){try{return (boolean)DRY.invoke(sampler);}catch(Exception e){throw new IllegalStateException(e);}}
    }
    List<String> cheap(Structure.GenerationContext c){
        var reasons=new ArrayList<String>();var layout=RuinKind.FOREST.layout(1);
        int margin=(int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8))+12;
        if(GreatTreeSites.reserved(c.randomState(),c.chunkPos().getMiddleBlockX(),c.chunkPos().getMiddleBlockZ(),margin))reasons.add("GREAT_TREE");
        if(GinsoStructure.reserved(c,margin))reasons.add("GINSO");
        // Fresh context: consuming this rotation must not change a caller's decision RNG.
        var p=RuinPlacement.atMinimum(layout,c.chunkPos().getWorldPosition(),Rotation.getRandom(c.random()));
        var entry=BlockPos.containing(p.point(layout.entry()));boolean biome=false;
        int delta=(int)Math.floor(layout.entry().y)-layout.surfaceY();
        for(int y=QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE+delta);y<=QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE+delta);y++)
            if(c.validBiome().test(c.biomeSource().getNoiseBiome(entry.getX()>>2,y,entry.getZ()>>2,c.randomState().sampler()))){biome=true;break;}
        if(!biome)reasons.add("BIOME_COARSE");return reasons;
    }
    private ForestSample full(ChunkPos cp){return samples.computeIfAbsent(cp.toLong(),unused->{
        long start=System.nanoTime();var c=context(RuinKind.FOREST,cp);var layout=RuinKind.FOREST.layout(1);
        var rotation=Rotation.getRandom(c.random());var footprint=RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation);
        var heights=new Heights(c);var relief=ForestSiteRules.inspect((x,z)->heights.at(footprint.block(new BlockPos(x,0,z))));
        var reasons=new LinkedHashSet<>(cheap(context(RuinKind.FOREST,cp)));reasons.addAll(relief.reasons());
        var door=RuinApproachPiece.doorway(RuinKind.FOREST,layout,footprint);
        int low=relief.coreMin(),high=relief.coreMax(),entryLow=Integer.MAX_VALUE,entryHigh=Integer.MIN_VALUE;
        for(int dx=-2;dx<=2;dx++){int y=heights.at(door.block(new BlockPos(dx,0,-1)));low=Math.min(low,y);high=Math.max(high,y);entryLow=Math.min(entryLow,y);entryHigh=Math.max(entryHigh,y);}
        if(!RuinSiteRules.footprintOk(low,high))reasons.add("ENTRY");
        var placed=new RuinPlacement(footprint.origin().atY(relief.anchor()-layout.surfaceY()),rotation);
        var doorway=RuinApproachPiece.doorway(RuinKind.FOREST,layout,placed);int[] approach=new int[45];
        for(int dx=-2;dx<=2;dx++)for(int dz=-9;dz<=-1;dz++){
            int y=heights.at(doorway.block(new BlockPos(dx,0,dz)));approach[(dx+2)*9+dz+9]=y;
            if(!RuinSiteRules.approachOk(y,doorway.origin().getY()))reasons.add("APPROACH");
        }
        var entry=BlockPos.containing(placed.point(layout.entry()));
        if(!c.validBiome().test(c.biomeSource().getNoiseBiome(entry.getX()>>2,entry.getY()>>2,entry.getZ()>>2,c.randomState().sampler())))reasons.add("BIOME_ENTRY");
        if(!heights.dry())reasons.add("WATER");
        if(!RuinSiteRules.fitsWorld(placed.origin().getY(),layout.size().getY(),level.getMinBuildHeight(),level.getMaxBuildHeight()))reasons.add("BUILD_HEIGHT");
        var site=new RuinDecision(RuinKind.FOREST,1,placed,Arrays.stream(approach).boxed().toList(),Map.of());
        if(reasons.isEmpty()&&!GinsoGenerationGuard.checkBounds(c.randomState(),site.boxes().getFirst().mutable()).allowed())reasons.add("GENERATED");
        String first=reasons.isEmpty()?"PASS":reasons.iterator().next();var data=new LinkedHashMap<String,Object>();
        data.put("chunk",List.of(cp.x,cp.z));data.put("policy",POLICY);data.put("terrainVersion",ForestSiteRules.POLICY_VERSION);
        data.put("rotation",rotation.name());data.put("origin",List.of(placed.origin().getX(),placed.origin().getY(),placed.origin().getZ()));
        data.put("firstReason",first);data.put("allReasons",List.copyOf(reasons));data.put("coreMin",relief.coreMin());data.put("coreMax",relief.coreMax());
        data.put("coreRelief",relief.coreMax()-relief.coreMin());data.put("exteriorMin",relief.exteriorMin());data.put("exteriorMax",relief.exteriorMax());
        data.put("exteriorRelief",relief.exteriorMax()-relief.exteriorMin());data.put("footprintMin",relief.footprintMin());data.put("footprintMax",relief.footprintMax());
        data.put("outerEnvelopeRelief",relief.footprintMax()-relief.footprintMin());data.put("anchor",relief.anchor());
        data.put("entryMin",entryLow);data.put("entryMax",entryHigh);data.put("entryCombinedRelief",high-low);
        data.put("heightQueries",heights.queried.size());data.put("exhaustiveMs",(System.nanoTime()-start)/1e6);
        return new ForestSample(new RuinStructure.SiteResult(reasons.isEmpty()?site:null,first),data);
    });}
    List<ChunkPos> neighbours(ChunkPos cp){
        var out=new ArrayList<ChunkPos>();int gx=Math.floorDiv(cp.x,spread.spacing()),gz=Math.floorDiv(cp.z,spread.spacing());
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0)out.add(spread.getPotentialStructureChunk(level.getSeed(),(gx+dx)*spread.spacing(),(gz+dz)*spread.spacing()));return out;
    }
    /** Policy/version isolation is per engine. No diagnostic forest result enters RuinSitingSites' production cache. */
    private final class Engine {
        final boolean proposed;
        final Map<String,RuinStructure.SiteResult> checks=new HashMap<>();
        final Map<Long,Optional<RuinDecision>> predicted=new HashMap<>(),selected=new HashMap<>();
        int yieldComparisons;
        Engine(boolean proposed){this.proposed=proposed;}
        RuinStructure.SiteResult base(RuinKind kind,ChunkPos cp){return checks.computeIfAbsent(kind.id+":"+cp.toLong(),unused->{
            if(!proposed||kind!=RuinKind.FOREST)return structure(kind).decision(context(kind,cp),1);
            var rejected=cheap(context(kind,cp));if(!rejected.isEmpty())return new RuinStructure.SiteResult(null,rejected.getFirst());
            return full(cp).result;
        });}
        Optional<RuinDecision> selection(ChunkPos cp,boolean applyYield){
            var cache=applyYield?selected:predicted;return cache.computeIfAbsent(cp.toLong(),unused->{
                var entries=new ArrayList<>(set.structures());var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
                int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
                while(!entries.isEmpty()){
                    int roll=random.nextInt(total),i=0;for(var e:entries){roll-=e.weight();if(roll<0)break;i++;}
                    var e=entries.remove(i);total-=e.weight();var kind=((RuinStructure)e.structure().value()).kind;
                    var result=base(kind,cp);if(result.site()!=null&&(!applyYield||kind!=RuinKind.CREEK||!denied(cp,result.site())))return Optional.of(result.site());
                }return Optional.empty();
            });
        }
        boolean denied(ChunkPos cp,RuinDecision own){
            boolean denied=false;
            for(var np:neighbours(cp)){
                var other=selection(np,false); // Exactly one layer; never query an adjacent yield decision.
                if(other.isPresent()&&RuinSitingSites.intersects(own,other.get(),true)){denied=true;break;}
            }
            if(!proposed){boolean production=RuinSitingSites.yield(context(RuinKind.CREEK,cp),own).denied();yieldComparisons++;
                if(production!=denied)throw new IllegalStateException("Audit R2 disagrees with production at "+cp);}
            return denied;
        }
        String first(ChunkPos cp){
            var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
            int roll=random.nextInt(set.structures().stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum());
            for(var e:set.structures()){roll-=e.weight();if(roll<0)return ((RuinStructure)e.structure().value()).kind.id;}throw new IllegalStateException();
        }
        Map<String,Object> run(List<ChunkPos> points,Map<Long,String> reference,Map<String,Object> report,String key){
            var counts=new LinkedHashMap<String,Integer>();for(String kind:List.of("forest_spring","creek_ruins","cave_altar","NONE"))counts.put(kind,0);
            var transitions=new LinkedHashMap<String,Integer>();var rows=new ArrayList<Map<String,Object>>();var out=new LinkedHashMap<String,Object>();
            out.put("policy",proposed?POLICY:"current_c_R2");out.put("counts",counts);out.put("transitions",transitions);out.put("rows",rows);report.put(key,out);
            long start=System.nanoTime();
            for(var cp:points){var site=selection(cp,true);String kind=site.map(s->s.kind().id).orElse("NONE");
                if(reference!=null&&!kind.equals(reference.get(cp.toLong())))throw new IllegalStateException("Archive kind drift "+cp+" expected "+reference.get(cp.toLong())+" actual "+kind);
                counts.merge(kind,1,Integer::sum);transitions.merge(first(cp)+" -> "+kind,1,Integer::sum);
                var row=new LinkedHashMap<String,Object>();row.put("chunk",List.of(cp.x,cp.z));row.put("first",first(cp));row.put("kind",kind);
                site.ifPresent(s->{row.put("rotation",s.placement().rotation().name());row.put("boxes",boxes(s));});rows.add(row);
                if(rows.size()%100==0){write(report);System.out.println("FOREST_STAGE_ONE "+key+" "+rows.size()+" "+counts);}
            }
            out.put("selectionWallMs",(System.nanoTime()-start)/1e6);out.put("referenceR2Comparisons",yieldComparisons);
            var pairs=new TreeMap<String,Map<String,Object>>();var hits=new HashMap<String,Set<Long>>();
            for(var cp:points){var own=selection(cp,true);if(own.isEmpty())continue;
                for(var np:neighbours(cp)){var other=selection(np,true);if(other.isEmpty()||!RuinSitingSites.intersects(own.get(),other.get(),false))continue;
                    long a=cp.toLong(),b=np.toLong();String pair=Math.min(a,b)+":"+Math.max(a,b);
                    pairs.putIfAbsent(pair,Map.of("chunk",List.of(cp.x,cp.z),"kind",own.get().kind().id,"boxes",boxes(own.get()),
                            "neighbour",List.of(np.x,np.z),"neighbourKind",other.get().kind().id,"neighbourBoxes",boxes(other.get())));
                    hits.computeIfAbsent(own.get().kind().id,k->new HashSet<>()).add(a);
                }
            }
            out.put("intersectionPairs",List.copyOf(pairs.values()));out.put("intersectionPairCount",pairs.size());
            var hitCounts=new TreeMap<String,Integer>();hits.forEach((k,v)->hitCounts.put(k,v.size()));out.put("intersectingGridSitesByKind",hitCounts);
            out.put("neighbourScope","8 neighbouring potential regions; includes outside-window starts; each pair deduplicated");
            return out;
        }
    }
    static List<List<Integer>> boxes(RuinDecision site){return site.boxes().stream().map(b->List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())).toList();}
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();report.put("status","running");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"forest-stage-one-worker"));
        try{executor.submit(()->{try{
            var audit=new ForestSitingAudit(server.overworld());report.put("seed",audit.level.getSeed());report.put("builderCalls",0);
            report.put("productionIntegration",false);report.put("policy",POLICY);report.put("terrainVersion",ForestSiteRules.POLICY_VERSION);
            var archived=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.forestStageOneReference")))).getAsJsonObject();
            if(audit.level.getSeed()!=archived.get("seed").getAsLong())throw new IllegalStateException("Wrong seed");
            var points=new ArrayList<ChunkPos>();var expected=new HashMap<Long,String>();
            for(var v:archived.getAsJsonArray("selectedRows")){var r=v.getAsJsonObject();var xy=r.getAsJsonArray("chunk");var cp=new ChunkPos(xy.get(0).getAsInt(),xy.get(1).getAsInt());
                if(!audit.spread.getPotentialStructureChunk(audit.level.getSeed(),cp.x,cp.z).equals(cp))throw new IllegalStateException("Not a grid start "+cp);
                points.add(cp);expected.put(cp.toLong(),r.get("kind").getAsString());}
            if(points.size()!=2025||expected.size()!=2025)throw new IllegalStateException("Wrong denominator");
            audit.new Engine(false).run(points,expected,report,"baseline");write(report);
            audit.new Engine(true).run(points,null,report,"proposal");write(report);
            // Complete distributions, including cheap rejects, are diagnostic-only extra queries.
            // They are not claimed as early-exit selection or production performance.
            var rows=new ArrayList<Map<String,Object>>();var first=new TreeMap<String,Integer>();var all=new TreeMap<String,Integer>();
            report.put("forestSamples",rows);report.put("firstReasons",first);report.put("allMatchedReasons",all);
            for(var cp:points){var sample=audit.full(cp);rows.add(sample.data);first.merge(sample.result.reason(),1,Integer::sum);
                for(var reason:(List<?>)sample.data.get("allReasons"))all.merge((String)reason,1,Integer::sum);
                if(rows.size()%100==0){write(report);System.out.println("FOREST_STAGE_ONE FULL_HEIGHTS "+rows.size());}}
            report.put("scope","All 2025 original grid starts, complete heights even for cheap rejects; proposal decisions and neighbour predictions never build pieces");
            report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> report){try{Files.writeString(ReportFiles.output(Path.of("forest-stage-one.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
}
