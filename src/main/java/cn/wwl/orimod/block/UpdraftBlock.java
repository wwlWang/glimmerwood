package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * R40 spirit updraft vent (after the wind columns of Ori): a low glowing vent. Gliding above it (up to
 * {@link #HEIGHT} blocks, with no solid block in between) lifts the player. Purely a movement aid; it
 * pushes nothing that is not gliding.
 */
public final class UpdraftBlock extends Block {
    public static final int HEIGHT = 12;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

    public UpdraftBlock(Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            try {
                level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), 0xBFF0FF, .22f, 40),
                        pos.getX() + random.nextDouble(), pos.getY() + .25, pos.getZ() + random.nextDouble(), 0, .12 + random.nextDouble() * .06, 0);
            } catch (Throwable ignored) { return; }
        }
    }

    /** Is there an updraft vent within {@link #HEIGHT} blocks below the entity's feet, with only open space between. */
    public static boolean lifting(Level level, Entity entity) {
        BlockPos.MutableBlockPos pos = entity.blockPosition().mutable();
        for (int dy = 0; dy <= HEIGHT; dy++, pos.move(0, -1, 0)) {
            if (!level.isLoaded(pos)) return false;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof UpdraftBlock) return true;
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return false;
        }
        return false;
    }
}
