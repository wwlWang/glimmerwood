package cn.wwl.orimod.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.*;

/** One-layer neighbour reservations. Predictions never call yield or build pieces. */
public final class RuinSitingSites {
    static final ResourceLocation SET=ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins");
    private record KindKey(ResourceLocation structure,int version,long chunk){}
    private record SelectionKey(ResourceLocation set,long chunk,GreatTreeSite.Site tree){}
    private record PlanKey(ResourceLocation set,long chunk){}
    private record Candidate(ResourceLocation structure,RuinKind kind,int version,List<RuinDecision.Box> boxes){}
    private static final class World {
        final RuinDecisionCache<KindKey,RuinStructure.SiteResult> terrain=new RuinDecisionCache<>(4096);
        final RuinDecisionCache<SelectionKey,Optional<RuinDecision>> selections=new RuinDecisionCache<>(2048);
        final RuinDecisionCache<PlanKey,List<Candidate>> plans=new RuinDecisionCache<>(2048);
    }
    // Values contain immutable decisions only, never a context or the weak key.
    private static final Map<RandomState,World> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    private static World world(RandomState state){synchronized(WORLDS){return WORLDS.computeIfAbsent(state,key->new World());}}
    static RuinStructure.SiteResult base(RuinStructure structure,Structure.GenerationContext c,int version){
        var layout=structure.kind.layout(version);
        int margin=version==0?56:(int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8));
        if(version>0&&structure.kind!=RuinKind.CAVE)margin+=12;
        if(GreatTreeSites.reserved(c.randomState(),c.chunkPos().getMiddleBlockX(),c.chunkPos().getMiddleBlockZ(),margin))return new RuinStructure.SiteResult(null,"GREAT_TREE");
        var id=c.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        var result=version==0||id==null?structure.evaluate(c,version):world(c.randomState()).terrain.get(
                new KindKey(id,version,c.chunkPos().toLong()),()->structure.evaluate(c,version));
        // Loaded/generated terrain is dynamic. Never cache permission to overwrite
        // it; the actual owner rechecks this guard even when reusing its decision.
        if(result.site()!=null&&version>0&&!GinsoGenerationGuard.checkBounds(c.randomState(),result.site().boxes().getFirst().mutable()).allowed())
            return new RuinStructure.SiteResult(null,"GENERATED");
        return result;
    }
    public static Optional<RuinDecision> prediction(Structure.GenerationContext c,ChunkPos pos){
        return world(c.randomState()).selections.get(new SelectionKey(SET,pos.toLong(),GreatTreeSites.site(c.randomState())),()->select(c,pos));
    }
    private static Optional<RuinDecision> select(Structure.GenerationContext c,ChunkPos pos){
        var set=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET);
        if(set==null)return Optional.empty();
        var entries=new ArrayList<>(set.structures());
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(c.seed(),pos.x,pos.z);
        int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while(!entries.isEmpty()){
            int roll=random.nextInt(total),index=0;
            for(var entry:entries){roll-=entry.weight();if(roll<0)break;index++;}
            var entry=entries.get(index);
            if(entry.structure().value() instanceof RuinStructure ruin){
                var next=new Structure.GenerationContext(c.registryAccess(),c.chunkGenerator(),c.biomeSource(),c.randomState(),
                        c.structureTemplateManager(),c.seed(),pos,c.heightAccessor(),ruin.biomes()::contains);
                var result=base(ruin,next,ruin.kind.currentVersion());
                if(result.site()!=null)return Optional.of(result.site());
            }
            entries.remove(index);total-=entry.weight();
        }
        return Optional.empty();
    }
    public record Yield(boolean denied,ChunkPos neighbour,String kind,int queried){}
    public static Yield yield(Structure.GenerationContext c,RuinDecision own){
        var set=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET);
        if(set==null||!(set.placement() instanceof RandomSpreadStructurePlacement placement)
                ||set.structures().stream().anyMatch(e->!(e.structure().value() instanceof RuinStructure)))
            return new Yield(false,null,"NONE",0);
        int gx=Math.floorDiv(c.chunkPos().x,placement.spacing()),gz=Math.floorDiv(c.chunkPos().z,placement.spacing()),queried=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0)continue;
            var cp=placement.getPotentialStructureChunk(c.seed(),(gx+dx)*placement.spacing(),(gz+dz)*placement.spacing());
            if(!possible(c,own,cp))continue;
            queried++;var neighbour=collisionSelection(c,cp,own);
            if(neighbour.isPresent()&&intersects(own,neighbour.get(),true))return new Yield(true,cp,neighbour.get().kind().id,queried);
        }
        return new Yield(false,null,"NONE",queried);
    }
    private static List<Candidate> plan(Structure.GenerationContext c,ChunkPos pos){
        return world(c.randomState()).plans.get(new PlanKey(SET,pos.toLong()),()->{
            var set=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET);
            var entries=new ArrayList<>(set.structures());var out=new ArrayList<Candidate>();
            var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(c.seed(),pos.x,pos.z);
            var rotationRandom=new WorldgenRandom(new LegacyRandomSource(0));rotationRandom.setLargeFeatureSeed(c.seed(),pos.x,pos.z);
            var rotation=Rotation.getRandom(rotationRandom);
            int total=entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
            while(!entries.isEmpty()){
                int roll=random.nextInt(total),index=0;
                for(var entry:entries){roll-=entry.weight();if(roll<0)break;index++;}
                var entry=entries.remove(index);total-=entry.weight();
                var ruin=(RuinStructure)entry.structure().value();int version=ruin.kind.currentVersion();
                var p=RuinPlacement.atMinimum(ruin.kind.layout(version),pos.getWorldPosition(),rotation);
                out.add(new Candidate(c.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(ruin),ruin.kind,version,RuinDecision.boxes(ruin.kind,version,p)));
            }
            return List.copyOf(out);
        });
    }
    private static Optional<RuinDecision> collisionSelection(Structure.GenerationContext c,ChunkPos pos,RuinDecision own){
        var cache=world(c.randomState());var key=new SelectionKey(SET,pos.toLong(),GreatTreeSites.site(c.randomState()));
        var known=cache.selections.completed(key);if(known!=null)return known;
        var order=plan(c,pos);var relevant=new boolean[order.size()];var ownBoxes=own.boxes();
        for(int i=0;i<order.size();i++)for(var a:ownBoxes)for(var b:order.get(i).boxes())relevant[i]|=a.intersects(b,true);
        var results=new ArrayList<Optional<RuinDecision>>(Collections.nCopies(order.size(),null));
        int winner=RuinCandidateOrder.relevantWinner(relevant,i->{
            var candidate=order.get(i);var ruin=(RuinStructure)c.registryAccess().registryOrThrow(Registries.STRUCTURE).get(candidate.structure());
            var next=new Structure.GenerationContext(c.registryAccess(),c.chunkGenerator(),c.biomeSource(),c.randomState(),
                    c.structureTemplateManager(),c.seed(),pos,c.heightAccessor(),ruin.biomes()::contains);
            var found=Optional.ofNullable(base(ruin,next,candidate.version()).site());results.set(i,found);return found.isPresent();
        });
        if(winner>=0){var result=results.get(winner);cache.selections.remember(key,result);return result;}
        // This is only a no-collision answer for this caller. A non-intersecting
        // kind may still be viable, so do not publish a false empty selection.
        return Optional.empty();
    }
    private static boolean possible(Structure.GenerationContext c,RuinDecision own,ChunkPos pos){
        var ownBoxes=own.boxes();
        // Each kind receives a fresh vanilla GenerationContext at this chunk;
        // its first random draw is the same rotation, independent of viability.
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(c.seed(),pos.x,pos.z);
        var rotation=Rotation.getRandom(random);
        for(var kind:RuinKind.values()){
            var p=RuinPlacement.atMinimum(kind.layout(kind.currentVersion()),pos.getWorldPosition(),rotation);
            for(var b:RuinDecision.boxes(kind,kind.currentVersion(),p))for(var a:ownBoxes)if(a.intersects(b,true))return true;
        }
        return false;
    }
    public static boolean intersects(RuinDecision a,RuinDecision b,boolean horizontal){
        for(var x:a.boxes())for(var y:b.boxes())if(x.intersects(y,horizontal))return true;return false;
    }
    private RuinSitingSites(){}
}
