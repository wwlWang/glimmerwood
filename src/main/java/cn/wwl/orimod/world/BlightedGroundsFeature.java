package cn.wwl.orimod.world;

import cn.wwl.orimod.block.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import java.util.*;

/** LOCAL_MODIFICATIONS: 1/160 chunks = 40,960 blocks² per attempt (~202-block grid spacing).
 * Whole-plan preflight precedes writes. Neighbors may already be decorated, so placement can still decline. */
public final class BlightedGroundsFeature extends Feature<NoneFeatureConfiguration> {
    public BlightedGroundsFeature(){super(NoneFeatureConfiguration.CODEC);}
    public enum Reason { OK, UNLOADED, BUILD_HEIGHT, SANCTUARY, RUIN, TERRAIN, CLEARANCE }
    public record Preflight(Reason reason,BlockPos center,Map<BlockPos,BlockState> writes) {}
    private static Preflight no(Reason reason,BlockPos center){return new Preflight(reason,center,Map.of());}
    public static boolean nearBlock(WorldGenLevel level,BlockPos center,int radius,Block block){
        int minY=Math.max(level.getMinBuildHeight(),center.getY()-radius),maxY=Math.min(level.getMaxBuildHeight()-1,center.getY()+radius);
        for(int cx=(center.getX()-radius)>>4;cx<=(center.getX()+radius)>>4;cx++)for(int cz=(center.getZ()-radius)>>4;cz<=(center.getZ()+radius)>>4;cz++){
            var chunk=level instanceof ServerLevel server?server.getChunkSource().getChunkNow(cx,cz):level.hasChunk(cx,cz)?level.getChunk(cx,cz,ChunkStatus.EMPTY,false):null;
            if(chunk==null)continue;
            for(int sy=minY>>4;sy<=maxY>>4;sy++){
                var section=chunk.getSection(chunk.getSectionIndex(sy<<4));if(!section.maybeHas(s->s.is(block)))continue;
                for(int x=Math.max(center.getX()-radius,cx<<4);x<=Math.min(center.getX()+radius,(cx<<4)+15);x++)
                    for(int z=Math.max(center.getZ()-radius,cz<<4);z<=Math.min(center.getZ()+radius,(cz<<4)+15);z++)
                        for(int y=Math.max(minY,sy<<4);y<=Math.min(maxY,(sy<<4)+15);y++)
                            if(section.getBlockState(x&15,y&15,z&15).is(block) && new BlockPos(x,y,z).distSqr(center)<=radius*radius)return true;
            }
        }
        return false;
    }
    public static Preflight preflight(WorldGenLevel level,BlockPos origin,long seed){
        var plan=BlightedGroundsShape.plan(seed);
        // No synchronous loads for commands; WorldGenRegion only reads its already supplied neighborhood.
        for(int x=(origin.getX()-14)>>4;x<=(origin.getX()+14)>>4;x++)for(int z=(origin.getZ()-14)>>4;z<=(origin.getZ()+14)>>4;z++)
            if(level instanceof ServerLevel server?server.getChunkSource().getChunkNow(x,z)==null:!level.hasChunk(x,z))return no(Reason.UNLOADED,origin);
        int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,origin.getX(),origin.getZ());
        var center=new BlockPos(origin.getX(),height,origin.getZ());
        if(RuinVegetationGuard.blocksTree(level,center,12))return no(Reason.RUIN,center);
        if(nearBlock(level,center,12,OriBlocks.SPIRIT_TREE_HEART.get()) || nearBlock(level,center,12,OriBlocks.GLOOM_CORE.get()))return no(Reason.SANCTUARY,center);
        var surface=new HashMap<BlockPos,BlockPos>();int min=height,max=height;
        for(var offset:plan.soil()){
            int x=center.getX()+offset.getX(),z=center.getZ()+offset.getZ();
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            min=Math.min(min,y);max=Math.max(max,y);
            var floor=new BlockPos(x,y-1,z);var state=level.getBlockState(floor);
            if(max-min>6 || Math.abs(y-height)>3 || !state.isFaceSturdy(level,floor,Direction.UP) || !state.getFluidState().isEmpty()
                    || state.hasBlockEntity() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                    || !(state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.CLAY)))return no(Reason.TERRAIN,center);
            surface.put(offset,floor);
        }
        var writes=new LinkedHashMap<BlockPos,BlockState>();
        for(var floor:surface.values())writes.put(floor,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());
        for(var offset:plan.pools())writes.put(surface.get(offset),OriBlocks.GLOOM_WATER.get().defaultBlockState());
        for(var offset:plan.thorns())writes.put(surface.get(offset).above(),OriBlocks.GLOOM_THORNS.get().defaultBlockState());
        for(var entry:plan.trees().entrySet()){
            var base=surface.get(entry.getKey()).above();int h=entry.getValue();
            for(int y=0;y<h;y++)writes.put(base.above(y),OriBlocks.WITHERED_SPIRIT_LOG.get().defaultBlockState());
            int dx=entry.getKey().getX()<0?-1:1;
            writes.put(base.offset(dx,h-2,0),OriBlocks.WITHERED_SPIRIT_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));
            writes.put(base.offset(dx,h-1,0),OriBlocks.WITHERED_SPIRIT_LOG.get().defaultBlockState());
        }
        writes.put(center,OriBlocks.GLOOM_CORE.get().defaultBlockState());
        for(var entry:writes.entrySet()){
            var p=entry.getKey();
            if(level.isOutsideBuildHeight(p) || !level.ensureCanWrite(p))return no(Reason.BUILD_HEIGHT,center);
            if(!surface.containsValue(p) && !SpiritTreeFeature.replaceable(level.getBlockState(p)))return no(Reason.CLEARANCE,center);
        }
        // Ensure the approach/core headroom is genuinely accessible, not just the crystal's own block.
        for(int y=1;y<=2;y++)if(!SpiritTreeFeature.replaceable(level.getBlockState(center.above(y))))return no(Reason.CLEARANCE,center);
        return new Preflight(Reason.OK,center,Collections.unmodifiableMap(writes));
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        var result=preflight(context.level(),context.origin(),context.random().nextLong());if(result.reason()!=Reason.OK)return false;
        for(var e:result.writes().entrySet())context.level().setBlock(e.getKey(),e.getValue(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        var positions=result.writes().keySet().stream().filter(p->!p.equals(result.center())).mapToLong(BlockPos::asLong).toArray();
        if(context.level().getBlockEntity(result.center()) instanceof GloomCoreBlockEntity core)core.terrain(positions);
        // Generation workers must never mutate the dimension SavedData. Core.onLoad registers after promotion.
        if(context.level() instanceof ServerLevel server)BlightData.get(server).register(result.center(),positions);
        return true;
    }
}
