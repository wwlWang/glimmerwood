package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** R57: a keystone resting in the ruin; taking it empties the pedestal for everyone (keystones open shared doors). */
public final class KeystonePedestalBlock extends Block {
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public KeystonePedestalBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FULL, true)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FULL); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    /** @return whether a keystone was taken. */
    public static boolean take(ServerPlayer player, BlockPos pos) {
        var level = player.level(); var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof KeystonePedestalBlock) || !state.getValue(FULL)) return false;
        level.setBlock(pos, state.setValue(FULL, false), 3);
        var stack = new ItemStack(cn.wwl.orimod.item.OriItems.KEYSTONE.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1, 1.4f);
        player.displayClientMessage(Component.translatable("message.orimod.keystone.found"), true);
        return true;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return state.getValue(FULL) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return player instanceof ServerPlayer server && take(server, pos) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FULL)) return;
        try {
            level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), 0xFFD27A, .45f, 12),
                    pos.getX() + .5, pos.getY() + .95, pos.getZ() + .5, 0, .003, 0);
        } catch (Throwable ignored) { }
    }
}
