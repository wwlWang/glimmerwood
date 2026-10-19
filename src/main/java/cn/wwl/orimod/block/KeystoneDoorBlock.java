package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * R57 keystone door (after Ori's keystone doors): a sealed ruin wall that opens once {@link #REQUIRED} keystones
 * are set into it. Every connected door block shows the shared progress; the whole door dissolves when it opens.
 */
public final class KeystoneDoorBlock extends Block {
    public static final int REQUIRED = 2, MAX_SIZE = 32;
    public static final IntegerProperty FILLED = IntegerProperty.create("filled", 0, REQUIRED - 1);

    public KeystoneDoorBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FILLED, 0)); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FILLED); }

    /** All door blocks connected to this one (face-adjacent), at most {@link #MAX_SIZE}. */
    public static List<BlockPos> door(Level level, BlockPos start) {
        var out = new ArrayList<BlockPos>(); var seen = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>();
        queue.add(start.immutable()); seen.add(start.immutable());
        while (!queue.isEmpty() && out.size() < MAX_SIZE) {
            var pos = queue.poll();
            if (!(level.getBlockState(pos).getBlock() instanceof KeystoneDoorBlock)) continue;
            out.add(pos);
            for (var d : Direction.values()) { var next = pos.relative(d); if (seen.add(next)) queue.add(next); }
        }
        return out;
    }

    /** Set one keystone from the stack into the door. @return keystones still needed (0 = opened), or -1 if refused. */
    public static int insert(ServerPlayer player, BlockPos pos, ItemStack stack) {
        var level = (ServerLevel) player.level();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof KeystoneDoorBlock)) return -1;
        int filled = state.getValue(FILLED);
        if (!stack.is(cn.wwl.orimod.item.OriItems.KEYSTONE.get())) {
            player.displayClientMessage(Component.translatable("message.orimod.keystone.need", filled, REQUIRED), true);
            return -1;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        var blocks = door(level, pos);
        if (filled + 1 >= REQUIRED) {
            for (var b : blocks) {
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                cn.wwl.orimod.fx.SpiritFx.emit(level, cn.wwl.orimod.fx.SpiritFxKind.CHECKPOINT_REACHED, b.getX() + .5, b.getY(), b.getZ() + .5, null);
            }
            level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.2f, .8f);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 1.5f);
            player.displayClientMessage(Component.translatable("message.orimod.keystone.opened"), true);
            return 0;
        }
        for (var b : blocks) level.setBlock(b, level.getBlockState(b).setValue(FILLED, filled + 1), 3);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1, 1.2f);
        player.displayClientMessage(Component.translatable("message.orimod.keystone.inserted", filled + 1, REQUIRED), true);
        return REQUIRED - filled - 1;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) insert(server, pos, stack);
        return ItemInteractionResult.CONSUME;
    }
}
