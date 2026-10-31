package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/** Ginso route vines retain the ordinary vine's support and collision rules. */
public final class GinsoVineBlock extends SpiritVineBlock implements LiquidBlockContainer {
    private static final com.mojang.serialization.MapCodec<SpiritVineBlock> GINSO_CODEC = simpleCodec(GinsoVineBlock::new);
    @Override public com.mojang.serialization.MapCodec<SpiritVineBlock> codec() { return GINSO_CODEC; }
    public GinsoVineBlock(Properties properties) { super(ProtectedBlock.durable(properties)); }
    @Override public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) { return false; }
    @Override public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) { return false; }
    @Override protected boolean canBeReplaced(BlockState state, Fluid fluid) { return false; }
}
