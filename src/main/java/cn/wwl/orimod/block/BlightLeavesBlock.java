package cn.wwl.orimod.block;

import net.minecraft.world.level.block.Block;

/** Journal-owned foliage: no decay, neighbor distance updates, random ticks or resource farming. */
public final class BlightLeavesBlock extends Block {
    public BlightLeavesBlock(Properties properties){super(properties);}
    @Override protected int getLightBlock(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos){return 1;}
}
