package cn.wwl.orimod.block;

import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** R96: Ori's Bash lantern, adapted as a craftable, non-colliding light that flares when struck by spirit abilities. */
public final class SpiritLanternBlock extends Block {
    /** R96: five seconds of bright light, with a dim resting glow for cave routes. */
    public static final int LIT_TICKS = 100, DIM_LIGHT = 7, LIT_LIGHT = 15;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(5, 3, 5, 11, 13, 11);

    public SpiritLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public static int lightLevel(boolean lit) { return lit ? LIT_LIGHT : DIM_LIGHT; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    /** R96: ignite or extend a loaded lantern; refreshing an already lit lantern never replays its burst. */
    public static boolean ignite(ServerLevel level, BlockPos pos, int ticks) {
        if (ticks <= 0 || !level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SpiritLanternBlock lantern)) return false;
        boolean lit = state.getValue(LIT);
        long now = level.getGameTime(), deadline = now + ticks;
        var chunk = level.getChunkAt(pos);
        // LevelChunk exposes TickContainerAccess, backed by its LevelChunkTicks in Minecraft 1.21.1.
        var scheduled = (net.minecraft.world.ticks.LevelChunkTicks<Block>) chunk.getBlockTicks();
        // LevelTicks ignores duplicate schedules. Its public chunk API lets us replace just this lantern's
        // pending tick, preserving any longer illumination and vanilla save/unload behavior without extra state.
        long previous = scheduled.getAll().filter(t -> t.type() == lantern && t.pos().equals(pos))
                .mapToLong(net.minecraft.world.ticks.ScheduledTick::triggerTick).max().orElse(now);
        if (lit && previous >= deadline) return true;
        if (lit) deadline = Math.max(deadline, previous);
        scheduled.removeIf(t -> t.type() == lantern && t.pos().equals(pos));
        if (!lit) level.setBlock(pos, state.setValue(LIT, true), UPDATE_ALL);
        level.scheduleTick(pos, lantern, (int) Math.min(Integer.MAX_VALUE, deadline - now));
        chunk.setUnsaved(true);
        if (!lit) SpiritFx.emit(level, SpiritFxKind.LANTERN_IGNITE, Vec3.atCenterOf(pos), null);
        return true;
    }

    /** R96: light lanterns whose centres lie in the sphere, without loading chunks; count includes refreshed lanterns. */
    public static int igniteAround(ServerLevel level, Vec3 centre, double radius, int ticks) {
        if (!Double.isFinite(radius) || radius < 0 || ticks <= 0) return 0;
        int lit = 0;
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(centre.subtract(radius, radius, radius)),
                BlockPos.containing(centre.add(radius, radius, radius)))) {
            if (pos.distToCenterSqr(centre) <= radius * radius && ignite(level, pos, ticks)) lit++;
        }
        return lit;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // A renewal can arrive after today's old tick was collected. The new pending tick owns the expiry.
        if (state.getValue(LIT) && !level.getBlockTicks().hasScheduledTick(pos, this))
            level.setBlock(pos, state.setValue(LIT, false), UPDATE_ALL);
    }
}
