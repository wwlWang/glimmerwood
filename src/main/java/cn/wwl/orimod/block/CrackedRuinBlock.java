package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** R103: ability-only stone. The scheduled snapshot survives chunk saves without a block entity. */
public final class CrackedRuinBlock extends ProtectedBlock {
    public static final BooleanProperty COLLAPSING = BooleanProperty.create("collapsing");
    public final CrackedRuin.Kind kind;
    public CrackedRuinBlock(CrackedRuin.Kind kind, Properties properties) {
        super(properties); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(COLLAPSING, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(COLLAPSING); }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) { return false; }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(COLLAPSING) && !level.getBlockTicks().hasScheduledTick(pos, this)) CrackedRuin.remove(level, pos, state);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        // A removed/replaced block must never lend its pending tick to a new collapse at the same position.
        if (level instanceof ServerLevel server && (!next.is(this) || !next.getValue(COLLAPSING))) {
            var ticks = (net.minecraft.world.ticks.LevelChunkTicks<Block>) server.getChunkAt(pos).getBlockTicks();
            ticks.removeIf(t -> t.type() == this && t.pos().equals(pos));
            server.getChunkAt(pos).setUnsaved(true);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
