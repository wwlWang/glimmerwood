package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** A dry, non-bouncing updraft root cushion. Even the full lookout fall deals no damage. */
public final class GreatTreeLandingBlock extends UpdraftBlock {
    public GreatTreeLandingBlock(Properties properties){super(properties);}
    // Taller than getOnPosLegacy's 0.2-block foot offset: fallOn must hit this cushion,
    // rather than the ordinary root floor below a three-pixel-thin vent.
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(0,0,0,16,4,16);}
    @Override public void fallOn(Level level,BlockState state,BlockPos pos,Entity entity,float distance){
        entity.fallDistance=0;
    }
}
