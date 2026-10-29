package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.*;

/** Diagnostic only: vanilla start decisions/pieces, never postProcess or chunk creation.
 * Context receives a plain height accessor, not the server level. Caches are audit-local.
 */
final class ForestExternalTruth {
    private final ServerLevel level;
    private final LevelHeightAccessor height;
    private final Map<String,External> cache = new LinkedHashMap<>();
    private final Map<Long,Rotation> rotations = new HashMap<>();
    private int builders;
    record External(boolean valid, List<BoundingBox> boxes, Map<String,Object> detail) {}

    ForestExternalTruth(ServerLevel level) {
        this.level=level;
        height=LevelHeightAccessor.create(level.getMinBuildHeight(),level.getHeight());
    }
    private Structure.GenerationContext context(Structure structure,ChunkPos cp,List<Map<String,Object>> biomeTests) {
        var generator=level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),
                level.getChunkSource().randomState(),level.getStructureManager(),level.getSeed(),cp,height,biome->{
                    boolean valid=structure.biomes().contains(biome);
                    biomeTests.add(Map.of("biome",biome.unwrapKey().map(k->k.location().toString()).orElse("direct"),"valid",valid));
                    return valid;
                });
    }
    private Structure glades() { return level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.fromNamespaceAndPath("orimod","glades_outpost")); }
    Rotation rotation(ChunkPos cp) { return rotations.computeIfAbsent(cp.toLong(),unused->
            Rotation.getRandom(context(glades(),cp,new ArrayList<>()).random())); }
    List<BoundingBox> rotatedGladesXZ(ChunkPos cp) {
        var p=GladesOutpostStructure.placement(cp.getWorldPosition(),rotation(cp));
        var main=p.blocks(BlockPos.ZERO,GladesOutpostPiece.SIZE.offset(-1,-1,-1));
        var approach=new RuinPlacement(p.block(GladesOutpostPiece.ENTRY),p.rotation())
                .blocks(new BlockPos(-2,0,-9),new BlockPos(2,0,-1));
        return List.of(xz(main),xz(approach));
    }
    External actual(Holder<StructureSet> set,ChunkPos cp) {
        String key=set.unwrapKey().orElseThrow().location()+"@"+cp.toLong();
        return cache.computeIfAbsent(key,unused->build(set,cp));
    }
    private External build(Holder<StructureSet> set,ChunkPos cp) {
        long begin=System.nanoTime();
        var detail=new LinkedHashMap<String,Object>();
        detail.put("set",set.unwrapKey().orElseThrow().location().toString());
        detail.put("chunk",List.of(cp.x,cp.z));
        var entries=new ArrayList<>(set.value().structures());
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        var trials=new ArrayList<Map<String,Object>>();detail.put("trials",trials);
        while(!entries.isEmpty()) {
            int i=0;
            // ChunkGenerator skips the selection RNG for a single-entry set.
            if(entries.size()>1) {
                int roll=random.nextInt(total);
                for(var e:entries){roll-=e.weight();if(roll<0)break;i++;}
            }
            var entry=entries.remove(i);total-=entry.weight();var structure=entry.structure().value();
            var row=new LinkedHashMap<String,Object>();trials.add(row);
            row.put("structure",entry.structure().unwrapKey().orElseThrow().location().toString());
            var biomeTests=new ArrayList<Map<String,Object>>();row.put("biomeTests",biomeTests);
            long decide=System.nanoTime();
            var stub=structure.findValidGenerationPoint(context(structure,cp,biomeTests));
            row.put("findValidMs",(System.nanoTime()-decide)/1e6);
            row.put("findValid",stub.isPresent());
            if(stub.isEmpty()) {
                // Independent, fresh RNG: diagnostic explanation only, never used to select a winner.
                if(structure instanceof JigsawStructure jigsaw) {
                    var raw=jigsaw.findGenerationPoint(context(structure,cp,new ArrayList<>()));
                    raw.ifPresent(s->row.put("rawStub",pos(s.position())));
                    row.put("reason",raw.isEmpty()?"NO_GENERATION_STUB":"BIOME_AT_REAL_STUB");
                } else if(structure instanceof GladesOutpostStructure outpost) {
                    var preflight=outpost.preflight(context(structure,cp,new ArrayList<>()));
                    row.put("preflightReason",preflight.reason().name());
                    row.put("preflightOrigin",pos(preflight.placement().origin()));
                    row.put("rotation",preflight.placement().rotation().name());
                }
                continue;
            }
            row.put("stub",pos(stub.get().position()));
            long build=System.nanoTime();builders++;
            var start=new StructureStart(structure,cp,0,stub.get().getPiecesBuilder().build());
            row.put("builderMs",(System.nanoTime()-build)/1e6);row.put("validStart",start.isValid());
            if(!start.isValid())continue;
            var boxes=start.getPieces().stream().map(p->copy(p.getBoundingBox())).toList();
            var pieces=new ArrayList<Map<String,Object>>();
            for(int p=0;p<start.getPieces().size();p++)pieces.add(Map.of("index",p,"class",start.getPieces().get(p).getClass().getName(),"box",box(boxes.get(p))));
            row.put("pieces",pieces);detail.put("selected",row.get("structure"));detail.put("valid",true);
            detail.put("totalMs",(System.nanoTime()-begin)/1e6);
            return new External(true,boxes,detail);
        }
        detail.put("valid",false);detail.put("totalMs",(System.nanoTime()-begin)/1e6);
        return new External(false,List.of(),detail);
    }
    boolean rejects(Holder<StructureSet> set,ChunkPos cp,RuinDecision own,boolean gladesRotationOnly) {
        if(gladesRotationOnly&&set.unwrapKey().orElseThrow().location().getPath().equals("glades_outpost"))
            return intersectVolume(own.boxes().stream().map(b->xz(b.mutable())).toList(),rotatedGladesXZ(cp))>0;
        return intersectVolume(own.boxes().stream().map(RuinDecision.Box::mutable).toList(),actual(set,cp).boxes)>0;
    }
    Map<String,Object> comparison(Holder<StructureSet> set,ChunkPos cp,RuinDecision own) {
        var external=actual(set,cp);var ownBoxes=own.boxes().stream().map(RuinDecision.Box::mutable).toList();
        var row=new LinkedHashMap<String,Object>();row.put("candidate",List.of(cp.x,cp.z));
        row.put("own",ForestSitingAudit.boxes(own));row.put("external",external.detail);
        row.put("intersection3D",intersectVolume(ownBoxes,external.boxes));
        row.put("intersection2D",intersectVolume(ownBoxes.stream().map(ForestExternalTruth::xz).toList(),external.boxes.stream().map(ForestExternalTruth::xz).toList()));
        row.put("nearestDistance3D",external.valid?distance(ownBoxes,external.boxes):null);
        row.put("classification",!external.valid?"NOT_GENERATED":((long)row.get("intersection3D")>0?"GENERATED_INTERSECTS":"GENERATED_DISJOINT"));
        if(set.unwrapKey().orElseThrow().location().getPath().equals("glades_outpost")) {
            row.put("deterministicRotation",rotation(cp).name());
            row.put("rotatedXZBoxes",rotatedGladesXZ(cp).stream().map(ForestExternalTruth::box).toList());
            row.put("rotatedIntersection2D",intersectVolume(ownBoxes.stream().map(ForestExternalTruth::xz).toList(),rotatedGladesXZ(cp)));
        }
        return row;
    }
    Map<String,Object> report(){return Map.of("diagnosticBuilders",builders,"starts",cache.values().stream().map(External::detail).toList(),
            "heightAccessor","LevelHeightAccessor.create; no LevelReader/chunk source passed to GenerationContext",
            "worldPlacementCalls",0,"scope","Actual vanilla findValidGenerationPoint + pieces only. No createStructures/postProcess/chunk mutation.");}
    static List<Integer> pos(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    static List<Integer> box(BoundingBox b){return List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
    private static BoundingBox copy(BoundingBox b){return new BoundingBox(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
    private static BoundingBox xz(BoundingBox b){return new BoundingBox(b.minX(),0,b.minZ(),b.maxX(),0,b.maxZ());}
    private static double distance(List<BoundingBox> a,List<BoundingBox> b){
        double best=Double.POSITIVE_INFINITY;
        for(var x:a)for(var y:b){double dx=Math.max(0,Math.max(x.minX()-y.maxX()-1,y.minX()-x.maxX()-1));
            double dy=Math.max(0,Math.max(x.minY()-y.maxY()-1,y.minY()-x.maxY()-1));
            double dz=Math.max(0,Math.max(x.minZ()-y.maxZ()-1,y.minZ()-x.maxZ()-1));best=Math.min(best,Math.sqrt(dx*dx+dy*dy+dz*dz));}
        return best;
    }
    /** Union volume of occupied cells, not double counting overlapping piece/own boxes. */
    static long intersectVolume(List<BoundingBox> a,List<BoundingBox> b) {
        var intersections=new ArrayList<BoundingBox>();var xs=new TreeSet<Integer>();
        for(var x:a)for(var y:b)if(x.intersects(y)) {
            var cut=new BoundingBox(Math.max(x.minX(),y.minX()),Math.max(x.minY(),y.minY()),Math.max(x.minZ(),y.minZ()),
                    Math.min(x.maxX(),y.maxX()),Math.min(x.maxY(),y.maxY()),Math.min(x.maxZ(),y.maxZ()));
            intersections.add(cut);xs.add(cut.minX());xs.add(cut.maxX()+1);
        }
        var edges=new ArrayList<>(xs);long volume=0;
        for(int i=1;i<edges.size();i++) {
            int lo=edges.get(i-1),hi=edges.get(i);var active=intersections.stream().filter(bx->bx.minX()<=lo&&bx.maxX()>=hi-1).toList();
            var ys=new TreeSet<Integer>();for(var box:active){ys.add(box.minY());ys.add(box.maxY()+1);}var ye=new ArrayList<>(ys);long area=0;
            for(int j=1;j<ye.size();j++) {
                int yl=ye.get(j-1),yh=ye.get(j);var zs=new ArrayList<int[]>();
                for(var box:active)if(box.minY()<=yl&&box.maxY()>=yh-1)zs.add(new int[]{box.minZ(),box.maxZ()+1});
                zs.sort(Comparator.comparingInt(v->v[0]));int end=Integer.MIN_VALUE;long span=0;
                for(var z:zs){int from=Math.max(end,z[0]);if(z[1]>from)span+=z[1]-from;end=Math.max(end,z[1]);}
                area+=(long)(yh-yl)*span;
            }
            volume+=(long)(hi-lo)*area;
        }
        return volume;
    }
}
