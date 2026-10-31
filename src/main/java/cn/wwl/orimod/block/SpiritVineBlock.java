package cn.wwl.orimod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** Cold luminous hanging foliage. No berries or random growth: the authored 2–5 block strands stay that length. */
public class SpiritVineBlock extends Block {
    public static final MapCodec<SpiritVineBlock> CODEC = simpleCodec(SpiritVineBlock::new);
    public static final BooleanProperty TIP = BooleanProperty.create("tip");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public SpiritVineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIP, true));
    }
    @Override public MapCodec<SpiritVineBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(TIP); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var above = level.getBlockState(pos.above());
        return above.is(this) || above.is(BlockTags.LEAVES) || above.isFaceSturdy(level, pos.above(), Direction.DOWN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(TIP, !context.getLevel().getBlockState(context.getClickedPos().below()).is(this));
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                               LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.UP && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return direction == Direction.DOWN ? state.setValue(TIP, !neighbour.is(this))
                : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
}
