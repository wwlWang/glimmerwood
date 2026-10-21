package cn.wwl.orimod.block;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

/** Solid translucent shell. Adjacent internal faces are culled, including under Sodium. */
public final class ShrineWardBlock extends HalfTransparentBlock {
    public ShrineWardBlock(Properties properties){super(properties);}
    @Override protected boolean isPathfindable(BlockState s,PathComputationType type){return false;}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){
        if(l instanceof net.minecraft.server.level.ServerLevel level && !old.is(this))l.scheduleTick(p,this,1);
    }
    @Override protected void tick(BlockState s,net.minecraft.server.level.ServerLevel l,BlockPos p,net.minecraft.util.RandomSource random){
        if(!cn.wwl.orimod.world.ShrineTrials.get(l).ownsWard(p))l.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
    }
}
