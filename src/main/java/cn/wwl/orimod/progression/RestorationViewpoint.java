package cn.wwl.orimod.progression;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Natural SpiritTreeShape entrances face north and end three blocks in front of the heart. */
public final class RestorationViewpoint {
    public static List<BlockPos> candidates(BlockPos heart){
        var result=new ArrayList<BlockPos>();
        for(int distance:new int[]{8,7,9})for(int side:new int[]{0,-1,1})for(int dy:new int[]{0,1,-1})
            result.add(heart.offset(side,dy,-distance));
        return List.copyOf(result);
    }
    public static Vec3 choose(ServerPlayer p,BlockPos heart){
        var level=p.serverLevel();var origin=p.position();
        for(var feet:candidates(heart)){
            var to=Vec3.atBottomCenterOf(feet);var movement=to.subtract(origin);
            var box=p.getBoundingBox().move(movement);
            // Conservative corridor test: never pull through roots, walls, other entities or an
            // unloaded chunk. Slopes without a clear swept body corridor keep the original spot.
            var corridor=p.getBoundingBox().expandTowards(movement).inflate(.03,0,.03);
            boolean loaded=true;
            for(int x=(int)Math.floor(corridor.minX)>>4;x<=((int)Math.floor(corridor.maxX)>>4);x++)
                for(int z=(int)Math.floor(corridor.minZ)>>4;z<=((int)Math.floor(corridor.maxZ)>>4);z++)
                    if(level.getChunkSource().getChunkNow(x,z)==null)loaded=false;
            if(!loaded || level.isOutsideBuildHeight(feet.below()) || level.isOutsideBuildHeight(feet.above(2))
                    || !level.getWorldBorder().isWithinBounds(box))continue;
            var floor=level.getBlockState(feet.below());
            if(!floor.isFaceSturdy(level,feet.below(),Direction.UP) || unsafe(floor)
                    || !level.noCollision(p,corridor)
                    || !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,box,
                            e->e!=p && e.isAlive() && !e.isSpectator()).isEmpty())continue;
            boolean safe=true;
            for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
                if(unsafe(level.getBlockState(pos)))safe=false;
            if(safe)return to;
        }
        return origin;
    }
    private static boolean unsafe(BlockState s){
        return !s.getFluidState().isEmpty() || s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW)
                || s.is(OriBlocks.GLOOM_THORNS) || s.is(OriBlocks.SPIRIT_PLATFORM) || CampfireBlock.isLitCampfire(s);
    }
    private RestorationViewpoint(){}
}
