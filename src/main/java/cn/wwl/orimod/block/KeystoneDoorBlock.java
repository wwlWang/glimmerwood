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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * R57 keystone door (after Ori's keystone doors): a sealed ruin wall that opens once {@link #REQUIRED} keystones
 * are set into it. Placed doors own nine parts; old saves and templates retain their connected-cell grouping.
 */
public final class KeystoneDoorBlock extends SanctuaryBlock {
    public static final int REQUIRED = 2, MAX_SIZE = 32;
    public static final IntegerProperty FILLED = IntegerProperty.create("filled", 0, REQUIRED - 1);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<KeystoneDoorPart> PART = EnumProperty.create("part", KeystoneDoorPart.class);
    private record Removal(Level level, BlockPos origin, Direction facing) {}
    private static final ThreadLocal<Set<Removal>> REMOVING = ThreadLocal.withInitial(HashSet::new);

    public KeystoneDoorBlock(Properties properties) {
        super(properties);
        // Missing properties in old chunk palettes/template NBT resolve to this state.
        registerDefaultState(stateDefinition.any().setValue(FILLED, 0).setValue(FACING, Direction.NORTH).setValue(PART, KeystoneDoorPart.LEGACY));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FILLED, FACING, PART); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(PART, KeystoneDoorPart.BOTTOM_CENTER);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(PART, state.getValue(PART).mirrored());
    }

    /** Owned nine-cell footprint, or face-adjacent LEGACY cells capped at {@link #MAX_SIZE}. */
    public static List<BlockPos> door(Level level, BlockPos start) {
        if (!level.hasChunkAt(start)) return List.of();
        var state = level.getBlockState(start);
        if (!(state.getBlock() instanceof KeystoneDoorBlock)) return List.of();
        if (state.getValue(PART) != KeystoneDoorPart.LEGACY) return ownedDoor(level, start, state);
        var out = new ArrayList<BlockPos>(); var seen = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>();
        queue.add(start.immutable()); seen.add(start.immutable());
        while (!queue.isEmpty() && out.size() < MAX_SIZE) {
            var pos = queue.poll();
            if (!level.hasChunkAt(pos)) continue;
            var cell = level.getBlockState(pos);
            if (!(cell.getBlock() instanceof KeystoneDoorBlock) || cell.getValue(PART) != KeystoneDoorPart.LEGACY) continue;
            out.add(pos);
            for (var d : Direction.values()) { var next = pos.relative(d); if (seen.add(next)) queue.add(next); }
        }
        return out;
    }

    private static List<BlockPos> ownedDoor(Level level, BlockPos pos, BlockState state) {
        var facing = state.getValue(FACING); var origin = state.getValue(PART).origin(pos, facing);
        var cells = new ArrayList<BlockPos>(9);
        for (var part : KeystoneDoorPart.PLACED) {
            var candidate = part.position(origin, facing);
            // Server cleanup may load this bounded footprint at a chunk edge; the client never loads chunks.
            if (level.isClientSide && !level.hasChunkAt(candidate)) continue;
            var other = level.getBlockState(candidate);
            if (other.is(state.getBlock()) && other.getValue(FACING) == facing && other.getValue(PART) == part) cells.add(candidate);
        }
        return cells;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (level.isClientSide || level.restoringBlockSnapshots || state.getValue(PART) == KeystoneDoorPart.LEGACY) return;
        if (next.is(this) && next.getValue(PART) == state.getValue(PART) && next.getValue(FACING) == state.getValue(FACING)) return;
        var facing = state.getValue(FACING);
        var removal = new Removal(level, state.getValue(PART).origin(pos, facing), facing);
        var removing = REMOVING.get();
        if (!removing.add(removal)) return;
        try {
            // Only the initiating removal can drop anything. The registered door still has no loot table.
            for (var cell : ownedDoor(level, pos, state)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        } finally {
            removing.remove(removal);
            if (removing.isEmpty()) REMOVING.remove();
        }
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
