package cn.wwl.orimod.world;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** New forests only. Geometry precedes noise; eligibility never invokes a pieces builder. */
final class ForestStructureReservations {
    private static final ResourceLocation GLADES=ResourceLocation.fromNamespaceAndPath("orimod","glades_outpost");
    private static final List<ResourceLocation> SETS=List.of(ResourceLocation.withDefaultNamespace("villages"),
            ResourceLocation.withDefaultNamespace("pillager_outposts"),GLADES);
    private record Key(ResourceLocation set,long chunk) {}
    record Qualification(ResourceLocation structure,BlockPos entry,List<RuinDecision.Box> boxes) {
        Qualification { entry=entry.immutable();boxes=List.copyOf(boxes); }
    }
    private static final class State {
        final ChunkGeneratorStructureState placements;
        final RuinDecisionCache<Key,Boolean> biomes=new RuinDecisionCache<>(8192);
        final RuinDecisionCache<Key,Optional<Qualification>> qualifications=new RuinDecisionCache<>(8192);
        State(Structure.GenerationContext c){
            placements=ChunkGeneratorStructureState.createForNormal(c.randomState(),c.seed(),c.biomeSource(),
                    c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup());
        }
    }
    // The registry-owned RuinStructure owns this instance; nothing survives its server/registry.
    private final ConcurrentHashMap<RandomState,State> states=new ConcurrentHashMap<>();

    String rejection(Structure.GenerationContext c,RuinDecision own){
        var state=states.computeIfAbsent(c.randomState(),ignored->new State(c));
        var registry=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var boxes=own.boxes();var envelope=envelope(boxes);
        for(var id:SETS){
            var holder=registry.getHolder(ResourceKey.create(Registries.STRUCTURE_SET,id));if(holder.isEmpty())continue;
            int radius=id.equals(GLADES)?48:96;
            for(int x=Math.floorDiv(envelope.minX()-radius,16);x<=Math.floorDiv(envelope.maxX()+radius,16);x++)
                for(int z=Math.floorDiv(envelope.minZ()-radius,16);z<=Math.floorDiv(envelope.maxZ()+radius,16);z++){
                    if(!holder.get().value().placement().isStructureChunk(state.placements,x,z))continue;
                    var cp=new ChunkPos(x,z);
                    if(!geometryPossible(id,c.seed(),cp,boxes))continue;
                    var key=new Key(id,cp.toLong());
                    if(!state.biomes.get(key,()->possibleBiome(c,holder.get(),cp)))continue;
                    var found=state.qualifications.get(key,()->qualify(c,holder.get(),cp));
                    if(found.isEmpty())continue;
                    if(!id.equals(GLADES)||intersects(boxes,found.get().boxes(),false))
                        return "STRUCTURE_"+id.getPath().toUpperCase(Locale.ROOT);
                }
        }
        return null;
    }
    static boolean geometryPossible(ResourceLocation id,long seed,ChunkPos cp,List<RuinDecision.Box> own){
        if(id.equals(GLADES)){
            var r=rotation(seed,cp);var at=GladesOutpostStructure.placement(cp.getWorldPosition(),r);
            return intersects(own,gladesBoxes(at),true);
        }
        var b=envelope(own);
        return cp.getMaxBlockX()>=b.minX()-96&&cp.getMinBlockX()<=b.maxX()+96
                &&cp.getMaxBlockZ()>=b.minZ()-96&&cp.getMinBlockZ()<=b.maxZ()+96;
    }
    private static BoundingBox envelope(List<RuinDecision.Box> boxes){
        var b=boxes.getFirst().mutable();for(var x:boxes)b.encapsulate(x.mutable());return b;
    }
    private static boolean intersects(List<RuinDecision.Box> own,List<RuinDecision.Box> other,boolean horizontal){
        for(var a:own)for(var b:other)if(a.intersects(b,horizontal))return true;return false;
    }
    private static Rotation rotation(long seed,ChunkPos cp){
        var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(seed,cp.x,cp.z);
        return Rotation.getRandom(random);
    }
    private static List<RuinDecision.Box> gladesBoxes(RuinPlacement at){
        var door=new RuinPlacement(at.block(GladesOutpostPiece.ENTRY),at.rotation());
        return List.of(RuinDecision.Box.of(at.blocks(BlockPos.ZERO,GladesOutpostPiece.SIZE.offset(-1,-1,-1))),
                RuinDecision.Box.of(door.blocks(new BlockPos(-2,-24,-9),new BlockPos(2,64,-1))));
    }
    private static boolean possibleBiome(Structure.GenerationContext c,Holder<StructureSet> set,ChunkPos cp){
        for(int dy:new int[]{0,-16,16,32,64}){
            var biome=c.biomeSource().getNoiseBiome(QuartPos.fromBlock(cp.getMiddleBlockX()),
                    QuartPos.fromBlock(c.chunkGenerator().getSeaLevel()+dy),QuartPos.fromBlock(cp.getMiddleBlockZ()),c.randomState().sampler());
            if(set.value().structures().stream().anyMatch(e->e.structure().value().biomes().contains(biome)))return true;
        }
        return false;
    }
    static Optional<Qualification> qualify(Structure.GenerationContext c,Holder<StructureSet> set,ChunkPos cp){
        return select(set.value().structures(),StructureSet.StructureSelectionEntry::weight,c.seed(),cp,entry->{
            var structure=entry.structure().value();
            // As in ChunkGenerator.tryGenerateStructure, each attempt gets a fresh context with the
            // same world seed/chunk. Never reuse the weighted selector or the forest's consumed RNG.
            var context=new Structure.GenerationContext(c.registryAccess(),c.chunkGenerator(),c.biomeSource(),c.randomState(),
                    c.structureTemplateManager(),c.seed(),cp,LevelHeightAccessor.create(c.heightAccessor().getMinBuildHeight(),c.heightAccessor().getHeight()),
                    structure.biomes()::contains);
            return structure.findValidGenerationPoint(context).map(stub->{
                List<RuinDecision.Box> boxes=List.of();
                if(structure instanceof GladesOutpostStructure){
                    var r=rotation(c.seed(),cp);var local=new RuinPlacement(BlockPos.ZERO,r).block(GladesOutpostPiece.ENTRY);
                    boxes=gladesBoxes(new RuinPlacement(stub.position().subtract(local),r));
                }
                return new Qualification(entry.structure().unwrapKey().orElseThrow().location(),stub.position(),boxes);
            });
        });
    }
    /** Exact vanilla StructureSet selection/failure removal; an originally singleton set skips the draw. */
    static <T,R> Optional<R> select(List<T> entries,ToIntFunction<T> weight,long seed,ChunkPos cp,Function<T,Optional<R>> attempt){
        if(entries.size()==1)return attempt.apply(entries.getFirst());
        var left=new ArrayList<>(entries);var random=new WorldgenRandom(new LegacyRandomSource(0));
        random.setLargeFeatureSeed(seed,cp.x,cp.z);int total=left.stream().mapToInt(weight).sum();
        while(!left.isEmpty()){
            int roll=random.nextInt(total),i=0;
            for(var e:left){roll-=weight.applyAsInt(e);if(roll<0)break;i++;}
            var entry=left.get(i);var found=attempt.apply(entry);if(found.isPresent())return found;
            left.remove(i);total-=weight.applyAsInt(entry);
        }
        return Optional.empty();
    }
}
