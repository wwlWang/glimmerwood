package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A full-height moss-root catch floor. No bounce, damage multiplier or special movement mode. */
public final class GinsoLandingBlock extends Block {
    public GinsoLandingBlock(Properties properties){super(properties);}
    @Override public void fallOn(Level level,BlockState state,BlockPos pos,Entity entity,float distance){entity.fallDistance=0;}
}
