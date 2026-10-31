package cn.wwl.orimod.world;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Deterministic placement grids and noise only. Never reads generated starts, blocks or chunk status. */
final class FeatureSiteReservations {
    private static final ResourceLocation RUINS=id("spirit_ruins"), GLADES=id("glades_outpost"), TREES=id("spirit_tree");
    private static final List<ResourceLocation> JIGSAW_SETS=List.of(ResourceLocation.withDefaultNamespace("villages"),ResourceLocation.withDefaultNamespace("pillager_outposts"));
    private record Key(ResourceLocation set,long chunk,GreatTreeSite.Site tree) {}
    private record RuinKey(long chunk,boolean withYield,GreatTreeSite.Site tree) {}
    private record TerrainKey(RuinKind kind,long chunk,GreatTreeSite.Site tree) {}
    private static final class State {
        final ChunkGeneratorStructureState placements;
        final RuinDecisionCache<RuinKey,Optional<RuinDecision>> ruins=new RuinDecisionCache<>(2048);
        final RuinDecisionCache<TerrainKey,Optional<RuinDecision>> terrain=new RuinDecisionCache<>(512);
        final RuinDecisionCache<Key,Optional<ForestStructureReservations.Qualification>> outposts=new RuinDecisionCache<>(256);
        final RuinDecisionCache<Key,Optional<FeatureSiteStructure.Accepted>> trees=new RuinDecisionCache<>(128);
        State(Structure.GenerationContext c){placements=ChunkGeneratorStructureState.createForNormal(c.randomState(),c.seed(),c.biomeSource(),c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup());}
    }
    private final ConcurrentHashMap<RandomState,State> states=new ConcurrentHashMap<>();
    private State state(Structure.GenerationContext c){return states.computeIfAbsent(c.randomState(),ignored->new State(c));}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("orimod",path);}

    String rejection(Structure.GenerationContext c,FeatureSitePlan plan,FeatureSiteStructure.Site site){
        var state=state(c);var sets=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);var origin=plan.base();
        var ruins=sets.get(RUINS);
        if(ruins!=null)for(int x=c.chunkPos().x-8;x<=c.chunkPos().x+8;x++)for(int z=c.chunkPos().z-8;z<=c.chunkPos().z+8;z++){
            if(!ruins.placement().isStructureChunk(state.placements,x,z))continue;
            var cp=new ChunkPos(x,z);
            if(ruins.structures().stream().noneMatch(e->possibleRuin(c,(RuinStructure)e.structure().value(),cp,origin)))continue;
            var found=collisionRuin(c,cp,origin);
            if(found.isPresent()){
                var decision=found.get();var structure=ruins.structures().stream().map(e->(RuinStructure)e.structure().value()).filter(r->r.kind==decision.kind()).findFirst().orElseThrow();
                if(inside(RuinDecision.Box.of(structure.adjustBoundingBox(envelope(decision.boxes()))),origin,14))return "RUIN_"+decision.kind().id;
            }
        }
        // Glades is our fixed two-piece structure, not a vanilla jigsaw expansion.
        var outpost=sets.getHolder(ResourceKey.create(Registries.STRUCTURE_SET,GLADES));
        if(outpost.isPresent())for(int x=c.chunkPos().x-4;x<=c.chunkPos().x+4;x++)for(int z=c.chunkPos().z-4;z<=c.chunkPos().z+4;z++){
            if(!outpost.get().value().placement().isStructureChunk(state.placements,x,z))continue;
            var cp=new ChunkPos(x,z);
            if(!inside(RuinDecision.Box.of(CreekStructureReservations.outpostEnvelope(cp)),origin,26))continue;
            var key=new Key(GLADES,cp.toLong(),GreatTreeSites.site(c.randomState()));
            var qualified=state.outposts.get(key,()->ForestStructureReservations.qualify(c,outpost.get(),cp));
            if(qualified.isPresent())for(var box:qualified.get().boxes())if(inside(box,origin,14))return "STRUCTURE_"+GLADES;
        }
        if(site==FeatureSiteStructure.Site.BLIGHTED_GROUNDS){
            var set=sets.get(TREES);
            if(set!=null)for(int x=c.chunkPos().x-2;x<=c.chunkPos().x+2;x++)for(int z=c.chunkPos().z-2;z<=c.chunkPos().z+2;z++){
                if(!set.placement().isStructureChunk(state.placements,x,z))continue;
                var cp=new ChunkPos(x,z);var key=new Key(TREES,cp.toLong(),GreatTreeSites.site(c.randomState()));
                var found=state.trees.get(key,()->ForestStructureReservations.select(set.structures(),StructureSet.StructureSelectionEntry::weight,c.seed(),cp,e->{
                    var tree=(FeatureSiteStructure)e.structure().value();return tree.choose(context(c,tree,cp));
                }));
                if(found.isPresent()&&conflicts(plan,found.get().plan()))return "SPIRIT_TREE";
            }
        }
        return null;
    }
    static boolean conflicts(FeatureSitePlan blight,FeatureSitePlan tree){
        if(tree.base().offset(SpiritTreeShape.HEART).closerThan(blight.base(),12))return true;
        for(var e:blight.writes().entrySet()){
            var other=tree.writes().get(e.getKey());
            if(other!=null&&!e.getValue().equals(other))return true;
        }
        return false;
    }
    private Optional<RuinDecision> collisionRuin(Structure.GenerationContext c,ChunkPos cp,BlockPos origin){
        var state=state(c);var key=new RuinKey(cp.toLong(),true,GreatTreeSites.site(c.randomState()));
        var cached=state.ruins.completed(key);if(cached!=null)return cached;
        var set=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(RUINS);
        var order=new ArrayList<RuinStructure>();
        ForestStructureReservations.select(set.structures(),StructureSet.StructureSelectionEntry::weight,c.seed(),cp,e->{order.add((RuinStructure)e.structure().value());return Optional.empty();});
        boolean[] relevant=new boolean[order.size()];
        for(int i=0;i<order.size();i++)relevant[i]=possibleRuin(c,order.get(i),cp,origin);
        var results=new ArrayList<Optional<RuinDecision>>(Collections.nCopies(order.size(),null));
        int winner=RuinCandidateOrder.relevantWinner(relevant,i->{
            var found=attempt(c,order.get(i),cp,true);results.set(i,found);return found.isPresent();
        });
        if(winner>=0){var found=results.get(winner);state.ruins.remember(key,found);return found;}
        // A non-intersecting winner may still exist. Do not poison the global selection cache with this caller's no-collision answer.
        return Optional.empty();
    }
    private Optional<RuinDecision> ruin(Structure.GenerationContext c,ChunkPos cp,boolean withYield){
        return state(c).ruins.get(new RuinKey(cp.toLong(),withYield,GreatTreeSites.site(c.randomState())),()->{
            var set=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(RUINS);
            if(set==null)return Optional.empty();
            return ForestStructureReservations.select(set.structures(),StructureSet.StructureSelectionEntry::weight,c.seed(),cp,e->attempt(c,(RuinStructure)e.structure().value(),cp,withYield));
        });
    }
    private Optional<RuinDecision> attempt(Structure.GenerationContext c,RuinStructure structure,ChunkPos cp,boolean withYield){
        var kind=structure.kind;int version=kind.currentVersion();var layout=kind.layout(version);
        int margin=version==0?56:(int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8));
        if(version>0&&kind!=RuinKind.CAVE)margin+=12;
        if(GreatTreeSites.reserved(c.randomState(),cp.getMiddleBlockX(),cp.getMiddleBlockZ(),margin))return Optional.empty();
        var next=context(c,structure,cp);
        // Yield depends only on horizontal boxes; reject an owner that must yield before its full terrain scan.
        if(withYield&&version>0&&kind!=RuinKind.CAVE){
            var at=RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation(c,cp));
            if(yields(next,RuinDecision.boxes(kind,version,at)))return Optional.empty();
        }
        return state(c).terrain.get(new TerrainKey(kind,cp.toLong(),GreatTreeSites.site(c.randomState())),()->{
            long started=System.nanoTime();var result=structure.evaluate(next,version);
            if(Boolean.getBoolean("orimod.worldgen.site.profile"))cn.wwl.orimod.OriMod.LOGGER.info("FEATURE_RUIN kind={} chunk={},{} ms={} reason={}",kind,cp.x,cp.z,(System.nanoTime()-started)/1e6,result.reason());
            return Optional.ofNullable(result.site());
        });
    }
    private boolean yields(Structure.GenerationContext c,List<RuinDecision.Box> own){
        var set=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(RUINS);
        if(!(set.placement() instanceof RandomSpreadStructurePlacement placement))return false;
        int gx=Math.floorDiv(c.chunkPos().x,placement.spacing()),gz=Math.floorDiv(c.chunkPos().z,placement.spacing());
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0)continue;
            var cp=placement.getPotentialStructureChunk(c.seed(),(gx+dx)*placement.spacing(),(gz+dz)*placement.spacing());
            boolean possible=false;
            for(var kind:RuinKind.values()){
                var at=RuinPlacement.atMinimum(kind.layout(kind.currentVersion()),cp.getWorldPosition(),rotation(c,cp));
                for(var a:own)for(var b:RuinDecision.boxes(kind,kind.currentVersion(),at))possible|=a.intersects(b,true);
            }
            if(!possible)continue;
            // Exactly one neighbour layer: never recurse into a neighbour's yield, so no cyclic futures.
            var other=ruin(c,cp,false);if(other.isPresent())for(var a:own)for(var b:other.get().boxes())if(a.intersects(b,true))return true;
        }
        return false;
    }
    /** Cheap conservative jigsaw reservation. No height queries, pieces builder or terrain-dependent biome. */
    String quickRejection(Structure.GenerationContext c,BlockPos origin){
        var state=state(c);var sets=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        for(var id:JIGSAW_SETS){
            var set=sets.get(id);if(set==null)continue;
            for(int x=(origin.getX()-110)>>4;x<=(origin.getX()+110)>>4;x++)for(int z=(origin.getZ()-110)>>4;z<=(origin.getZ()+110)>>4;z++){
                if(!set.placement().isStructureChunk(state.placements,x,z))continue;
                var cp=new ChunkPos(x,z);if(!jigsawEnvelope(cp,origin))continue;
                var biome=c.biomeSource().getNoiseBiome(QuartPos.fromBlock(cp.getMiddleBlockX()),QuartPos.fromBlock(c.chunkGenerator().getSeaLevel()),QuartPos.fromBlock(cp.getMiddleBlockZ()),c.randomState().sampler());
                if(set.structures().stream().anyMatch(e->e.structure().value().biomes().contains(biome)))return "STRUCTURE_"+id;
            }
        }
        return null;
    }
    static boolean jigsawEnvelope(ChunkPos cp,BlockPos p){
        // Vanilla villages/outposts have max_distance_from_center=80; include the feature's own 14-block radius.
        int margin=80+FeatureSiteStructure.Site.RADIUS;
        return p.getX()>=cp.getMinBlockX()-margin&&p.getX()<=cp.getMaxBlockX()+margin
                &&p.getZ()>=cp.getMinBlockZ()-margin&&p.getZ()<=cp.getMaxBlockZ()+margin;
    }
    static Structure.GenerationContext context(Structure.GenerationContext c,Structure structure,ChunkPos cp){
        return new Structure.GenerationContext(c.registryAccess(),c.chunkGenerator(),c.biomeSource(),c.randomState(),c.structureTemplateManager(),c.seed(),cp,
                LevelHeightAccessor.create(c.heightAccessor().getMinBuildHeight(),c.heightAccessor().getHeight()),structure.biomes()::contains);
    }
    static boolean inside(RuinDecision.Box box,BlockPos p,int margin){
        return p.getX()>=box.minX()-margin&&p.getX()<=box.maxX()+margin&&p.getZ()>=box.minZ()-margin&&p.getZ()<=box.maxZ()+margin;
    }
    private static Rotation rotation(Structure.GenerationContext c,ChunkPos cp){
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(c.seed(),cp.x,cp.z);return Rotation.getRandom(random);
    }
    private static boolean possibleRuin(Structure.GenerationContext c,RuinStructure ruin,ChunkPos cp,BlockPos origin){
        var p=RuinPlacement.atMinimum(ruin.kind.layout(ruin.kind.currentVersion()),cp.getWorldPosition(),rotation(c,cp));
        return inside(RuinDecision.Box.of(ruin.adjustBoundingBox(envelope(RuinDecision.boxes(ruin.kind,ruin.kind.currentVersion(),p)))),origin,14);
    }
    private static BoundingBox envelope(List<RuinDecision.Box> boxes){var b=boxes.getFirst().mutable();for(var x:boxes)b.encapsulate(x.mutable());return b;}
}
