package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.JsonArray;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;

import java.util.*;

/** Implementable, finite R1/R2 policies. Neighbour predictions NEVER call yield. */
final class CreekBoundedYieldAudit {
    private final CreekActualNeighbourAudit oracle, predictor;
    private final ServerLevel level;
    private final String policy, rule;
    private final Map<String,BoundingBox> envelopes=new HashMap<>();
    private final Map<String,Boolean> cheapCache=new HashMap<>();
    private final Map<Long,Map<String,Object>> decisions=new LinkedHashMap<>();
    private long biomeQueries;

    CreekBoundedYieldAudit(CreekActualNeighbourAudit oracle,String policy,String rule)throws Exception{
        if(!Set.of("c","d4r8","d8r8").contains(policy))throw new IllegalArgumentException("Unsupported bounded audit policy");
        this.oracle=oracle;this.policy=policy;this.rule=rule;level=oracle.level();
        // Separate cold predictor cache: the earlier complete oracle must not
        // make R2's marginal costs appear to be free.
        predictor=rule.equals("R2")?new CreekActualNeighbourAudit(level):null;
    }
    private Structure.GenerationContext context(RuinKind kind,ChunkPos cp){
        var cs=level.getChunkSource();var g=cs.getGenerator();var s=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());
        return new Structure.GenerationContext(level.registryAccess(),g,g.getBiomeSource(),cs.randomState(),level.getStructureManager(),level.getSeed(),cp,level,s.biomes()::contains);
    }
    private List<BlockPos> offsets(RuinKind kind){
        var out=new ArrayList<BlockPos>();out.add(BlockPos.ZERO);
        if(kind==RuinKind.CREEK&&!policy.equals("c"))for(int i=0;i<8;i+=policy.equals("d4r8")?2:1)out.add(new BlockPos((int)Math.round(8*Math.cos(i*Math.PI/4)),0,(int)Math.round(8*Math.sin(i*Math.PI/4))));return out;
    }
    private BoundingBox envelope(RuinKind kind,ChunkPos cp){return envelopes.computeIfAbsent(kind.id+":"+cp.toLong(),unused->{
        var layout=kind.layout(kind.currentVersion());BoundingBox result=null;
        for(var offset:offsets(kind))for(var rotation:Rotation.values()){
            var base=RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation);var p=new RuinPlacement(base.origin().offset(offset),rotation);
            var b=p.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1));int skirt=kind==RuinKind.CAVE?0:8;
            b=new BoundingBox(b.minX()-skirt,0,b.minZ()-skirt,b.maxX()+skirt,0,b.maxZ()+skirt);
            int width=kind==RuinKind.CAVE?4:2;var a=RuinApproachPiece.doorway(kind,layout,p).blocks(new BlockPos(-width,0,-9),new BlockPos(width,0,-1));
            b.encapsulate(new BoundingBox(a.minX(),0,a.minZ(),a.maxX(),0,a.maxZ()));
            if(result==null)result=b;else result.encapsulate(b);
        }return result;
    });}
    private boolean cheap(RuinKind kind,ChunkPos cp){return cheapCache.computeIfAbsent(kind.id+":"+cp.toLong(),unused->{
        var layout=kind.layout(kind.currentVersion());var c=context(kind,cp);
        int margin=(int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8))+(kind==RuinKind.CAVE?0:12);
        for(var offset:offsets(kind)){
            var shifted=new ChunkPos(new BlockPos(cp.getMiddleBlockX()+offset.getX(),0,cp.getMiddleBlockZ()+offset.getZ()));
            if(GinsoStructure.reserved(context(kind,shifted),margin))continue;
            for(var rotation:Rotation.values()){
                var base=RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation);var p=new RuinPlacement(base.origin().offset(offset),rotation);
                var entry=BlockPos.containing(p.point(layout.entry()));int delta=(int)Math.floor(layout.entry().y)-layout.surfaceY();
                for(int y=QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE+delta);y<=QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE+delta);y++){
                    biomeQueries++;
                    if(c.validBiome().test(c.biomeSource().getNoiseBiome(entry.getX()>>2,y,entry.getZ()>>2,c.randomState().sampler())))return true;
                }
            }
        }return false;
    });}
    private static boolean intersects(CreekActualNeighbourAudit.Site own,BoundingBox box){return own.boxes().stream().anyMatch(b->b.intersects(box.minX(),box.minZ(),box.maxX(),box.maxZ()));}
    private Map<String,Object> evaluate(ChunkPos cp){
        var own=oracle.check(policy,RuinKind.CREEK,cp).orElseThrow();
        var biomeState=CreekAuditBiomeCache.capture(level.getChunkSource().getGenerator().getBiomeSource());
        try{
            long start=System.nanoTime(),q=biomeQueries;int cold=predictor==null?0:predictor.cachedSelected(),checks=predictor==null?0:predictor.cachedChecks(),requested=0,visited=0;
            boolean deny=false;String reason="NONE";List<Integer> hit=List.of();
            for(var np:oracle.neighbours(cp)){
                visited++;
                if(rule.equals("R1")){
                    for(var kind:List.of(RuinKind.FOREST,RuinKind.CREEK,RuinKind.CAVE)){
                        if(intersects(own,envelope(kind,np))&&cheap(kind,np)){deny=true;reason=kind.id;hit=List.of(np.x,np.z);break;}
                    }
                }else{
                    boolean possible=false;for(var kind:List.of(RuinKind.FOREST,RuinKind.CREEK,RuinKind.CAVE))possible|=intersects(own,envelope(kind,np));
                    if(!possible)continue;
                    requested++;var found=predictor.selected(policy,np); // one layer, no yield callback
                    if(found.isPresent()&&CreekActualNeighbourAudit.overlap(own,found.get(),true)){deny=true;reason=found.get().kind();hit=List.of(np.x,np.z);}
                }
                if(deny)break;
            }
            var out=new LinkedHashMap<String,Object>();out.put("thread",Thread.currentThread().getName());out.put("chunk",List.of(cp.x,cp.z));out.put("deny",deny);out.put("hitKind",reason);out.put("hitChunk",hit);
            out.put("ms",(System.nanoTime()-start)/1e6);out.put("biomeQueries",biomeQueries-q);out.put("requestedNeighbours",requested);out.put("potentialNeighboursVisited",visited);
            out.put("coldNeighbourChecks",predictor==null?0:predictor.cachedSelected()-cold);out.put("kindChecks",predictor==null?0:predictor.cachedChecks()-checks);
            out.put("heightQueries",rule.equals("R1")?0:"included in full neighbour checks");return out;
        }finally{biomeState.restore();}
    }
    private Map<String,Object> decision(ChunkPos cp){return decisions.computeIfAbsent(cp.toLong(),unused->evaluate(cp));}
    private Optional<CreekActualNeighbourAudit.Site> resolved(ChunkPos cp){
        var initial=oracle.selected(policy,cp);
        return initial.isPresent()&&initial.get().kind().equals("creek_ruins")&&Boolean.TRUE.equals(decision(cp).get("deny"))?oracle.fallback(policy,cp):initial;
    }
    static Map<String,Object> run(CreekActualNeighbourAudit oracle,String policy,JsonArray points,boolean realGrid)throws Exception{
        var result=new LinkedHashMap<String,Object>();
        for(String rule:List.of("R1","R2")){
            var a=new CreekBoundedYieldAudit(oracle,policy,rule);var rows=new ArrayList<Map<String,Object>>();var accepted=new ArrayList<ChunkPos>();
            for(var value:points){var r=value.getAsJsonObject();if(!r.get("accepted").getAsBoolean())continue;
                var q=r.getAsJsonArray("chunk");var cp=new ChunkPos(q.get(0).getAsInt(),q.get(1).getAsInt());accepted.add(cp);rows.add(a.decision(cp));
                if(rows.size()%50==0)System.out.println("BOUNDED_YIELD "+policy+" "+rule+" "+rows.size());
            }
            var out=new LinkedHashMap<String,Object>();out.put("rawAccepted",rows.size());out.put("staticRemaining",rows.stream().filter(r->!Boolean.TRUE.equals(r.get("deny"))).count());out.put("firstPassCosts",rows);
            var warm=new ArrayList<Map<String,Object>>();for(var cp:accepted){var m=a.evaluate(cp);if(!m.get("deny").equals(a.decisions.get(cp.toLong()).get("deny")))throw new IllegalStateException("Yield cache changed decision "+cp);warm.add(m);}out.put("warmPassCosts",warm);
            {
                var initial=new LinkedHashMap<String,Integer>();var after=new LinkedHashMap<String,Integer>();
                var initialTransitions=new LinkedHashMap<String,Integer>();var finalTransitions=new LinkedHashMap<String,Integer>();
                for(String k:List.of("creek_ruins","forest_spring","cave_altar","NONE")){initial.put(k,0);after.put(k,0);}
                var survivors=new ArrayList<ChunkPos>();var collisions=new ArrayList<Map<String,Object>>();int horizontal=0,volume=0,excluded=0;
                for(var value:points){var q=value.getAsJsonObject().getAsJsonArray("chunk");var cp=new ChunkPos(q.get(0).getAsInt(),q.get(1).getAsInt());
                    if(!oracle.potential(cp)){excluded++;continue;}
                    initial.merge(oracle.selected(policy,cp).map(CreekActualNeighbourAudit.Site::kind).orElse("NONE"),1,Integer::sum);
                    var site=a.resolved(cp);after.merge(site.map(CreekActualNeighbourAudit.Site::kind).orElse("NONE"),1,Integer::sum);
                    String first=oracle.firstKind(cp);initialTransitions.merge(first+" -> "+oracle.selected(policy,cp).map(CreekActualNeighbourAudit.Site::kind).orElse("NONE"),1,Integer::sum);
                    finalTransitions.merge(first+" -> "+site.map(CreekActualNeighbourAudit.Site::kind).orElse("NONE"),1,Integer::sum);
                    if(site.isPresent()&&site.get().kind().equals("creek_ruins"))survivors.add(cp);
                }
                for(var cp:survivors){var own=a.resolved(cp).orElseThrow();boolean xz=false,xyz=false;var hits=new ArrayList<Map<String,Object>>();
                    for(var np:oracle.neighbours(cp)){var n=a.resolved(np);if(n.isEmpty())continue;
                        boolean h=CreekActualNeighbourAudit.overlap(own,n.get(),true),v=CreekActualNeighbourAudit.overlap(own,n.get(),false);xz|=h;xyz|=v;
                        if(h)hits.add(Map.of("chunk",List.of(np.x,np.z),"site",CreekActualNeighbourAudit.describe(n),"volume",v));
                    }
                    if(xz)horizontal++;if(xyz)volume++;if(xz)collisions.add(Map.of("chunk",List.of(cp.x,cp.z),"own",CreekActualNeighbourAudit.describe(Optional.of(own)),"hits",hits));
                }
                var changes=new LinkedHashMap<String,Integer>();for(String k:initial.keySet())changes.put(k,after.get(k)-initial.get(k));
                if(realGrid&&excluded!=0)throw new IllegalStateException("Real grid contained non-potential points");
                out.put("samplePoints",points.size());out.put("excludedNonPotential",excluded);out.put("potentialStarts",points.size()-excluded);
                out.put("initialTransitions",initialTransitions);out.put("finalTransitions",finalTransitions);
                out.put("initialCounts",initial);out.put("finalCounts",after);out.put("kindChanges",changes);out.put("finalCreekHorizontalCollisions",horizontal);out.put("finalCreekWriteBoxCollisions",volume);out.put("collisionDetails",collisions);
                System.out.println("BOUNDED_YIELD_FINAL "+policy+" "+rule+" "+initial+" -> "+after+" collisions="+horizontal+"/"+volume);
            }
            result.put(rule,out);
        }return result;
    }
}
