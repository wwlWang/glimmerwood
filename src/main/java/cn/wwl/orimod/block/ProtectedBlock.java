package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;

/** Environmental protection for progression blocks. Hardness and collision remain the caller's choice. */
public class ProtectedBlock extends Block implements LiquidBlockContainer {
    public ProtectedBlock(Properties properties) { super(durable(properties)); }
    public static Properties durable(Properties properties) {
        return properties.explosionResistance(3600000).pushReaction(PushReaction.BLOCK);
    }
    // FlowingFluid asks LiquidBlockContainer before blocksMotion; buckets also ask canBeReplaced.
    @Override public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) { return false; }
    @Override public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) { return false; }
    @Override protected boolean canBeReplaced(BlockState state, Fluid fluid) { return false; }
}
