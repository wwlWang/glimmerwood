package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Holder;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Candidate-grid reservation only: no chunks, terrain queries or structure builders.
 * Owned by a registry's RuinStructure, so the cache dies with that server/registry. */
final class CreekStructureReservations {
    static final List<ResourceLocation> SETS=List.of(ResourceLocation.withDefaultNamespace("villages"),
            ResourceLocation.withDefaultNamespace("pillager_outposts"),ResourceLocation.fromNamespaceAndPath("orimod","glades_outpost"));
    private record BiomeKey(ResourceLocation set,long chunk){}
    private static final class State {
        final ChunkGeneratorStructureState placements;
        final RuinDecisionCache<BiomeKey,Boolean> biomes=new RuinDecisionCache<>(8192);
        State(ChunkGeneratorStructureState placements){this.placements=placements;}
    }
    private final ConcurrentHashMap<RandomState,State> states=new ConcurrentHashMap<>();
    static int radius(int width,int depth){return (Math.max(width,depth)+1)/2+12+16;}
    static int chunkRadius(int width,int depth){return (radius(width,depth)+15)/16;}
    String rejection(Structure.GenerationContext c,RuinPlacement footprint,RuinKind.Layout layout){
        var registry=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var state=states.computeIfAbsent(c.randomState(),ignored->new State(ChunkGeneratorStructureState.createForNormal(
                c.randomState(),c.seed(),c.biomeSource(),registry.asLookup())));
        var centre=new ChunkPos(footprint.block(new BlockPos(layout.size().getX()/2,0,layout.size().getZ()/2)));
        int range=chunkRadius(layout.size().getX(),layout.size().getZ());
        var own=footprint.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1));
        own=new BoundingBox(own.minX()-8,0,own.minZ()-8,own.maxX()+8,0,own.maxZ()+8);
        var approach=RuinApproachPiece.doorway(RuinKind.CREEK,layout,footprint).blocks(new BlockPos(-2,0,-9),new BlockPos(2,0,-1));
        own.encapsulate(new BoundingBox(approach.minX(),0,approach.minZ(),approach.maxX(),0,approach.maxZ()));
        for(var id:SETS){
            var holder=registry.getHolder(ResourceKey.create(Registries.STRUCTURE_SET,id));
            if(holder.isEmpty())continue;
            boolean exact=id.getPath().equals("glades_outpost");
            int minX=centre.x-range,maxX=centre.x+range,minZ=centre.z-range,maxZ=centre.z+range;
            if(exact){minX=Math.floorDiv(own.minX()-48,16);maxX=Math.floorDiv(own.maxX()+48,16);minZ=Math.floorDiv(own.minZ()-48,16);maxZ=Math.floorDiv(own.maxZ()+48,16);}
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                if(!holder.get().value().placement().isStructureChunk(state.placements,x,z))continue;
                var cp=new ChunkPos(x,z);
                if(exact&&!own.intersects(outpostEnvelope(cp)))continue;
                if(state.biomes.get(new BiomeKey(id,cp.toLong()),()->possibleBiome(c,holder.get(),cp)))
                    return "STRUCTURE_"+id.getPath().toUpperCase(java.util.Locale.ROOT);
            }
        }
        return null;
    }
    private static boolean possibleBiome(Structure.GenerationContext c,Holder<StructureSet> set,ChunkPos cp){
        for(int offset:new int[]{0,-16,16,32,64}){
            var biome=c.biomeSource().getNoiseBiome(QuartPos.fromBlock(cp.getMiddleBlockX()),QuartPos.fromBlock(c.chunkGenerator().getSeaLevel()+offset),
                    QuartPos.fromBlock(cp.getMiddleBlockZ()),c.randomState().sampler());
            if(set.value().structures().stream().anyMatch(e->e.structure().value().biomes().contains(biome)))return true;
        }
        return false;
    }
    static BoundingBox outpostEnvelope(ChunkPos cp){
        BoundingBox result=null;
        for(var rotation:Rotation.values()){
            var p=GladesOutpostStructure.placement(cp.getWorldPosition(),rotation);
            var b=p.blocks(BlockPos.ZERO,GladesOutpostPiece.SIZE.offset(-1,-1,-1));
            b=new BoundingBox(b.minX(),0,b.minZ(),b.maxX(),0,b.maxZ());
            var approach=new RuinPlacement(p.block(GladesOutpostPiece.ENTRY),rotation).blocks(new BlockPos(-2,0,-9),new BlockPos(2,0,-1));
            b.encapsulate(new BoundingBox(approach.minX(),0,approach.minZ(),approach.maxX(),0,approach.maxZ()));
            if(result==null)result=b;else result.encapsulate(b);
        }
        return result;
    }
}
