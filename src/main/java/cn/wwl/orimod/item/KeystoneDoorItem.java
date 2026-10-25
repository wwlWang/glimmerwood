package cn.wwl.orimod.item;

import cn.wwl.orimod.block.KeystoneDoorBlock;
import cn.wwl.orimod.block.KeystoneDoorPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.ArrayList;
import java.util.function.BiPredicate;

/** A single vanilla item placement/consumption, with preflight and rollback for all nine occupied cells. */
public final class KeystoneDoorItem extends BlockItem {
    public KeystoneDoorItem(KeystoneDoorBlock block, Properties properties) { super(block, properties); }

    @Override public InteractionResult place(BlockPlaceContext context) {
        // Ownership and progress come from placement, never from a copied/command-authored block-state component.
        var stack = context.getItemInHand(); var properties = stack.remove(DataComponents.BLOCK_STATE);
        try { return super.place(context); }
        finally { if (properties != null) stack.set(DataComponents.BLOCK_STATE, properties); }
    }

    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        var state = super.getPlacementState(context);
        return state != null && canPlaceDoor(context, state) ? state : null;
    }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return placeParts(context, state, (pos, part) -> context.getLevel().setBlock(pos, part, Block.UPDATE_ALL));
    }

    private static boolean canPlaceDoor(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel(); var player = context.getPlayer(); var origin = context.getClickedPos();
        var collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        for (var part : KeystoneDoorPart.PLACED) {
            var pos = part.position(origin, state.getValue(KeystoneDoorBlock.FACING));
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) return false;
            var cellContext = BlockPlaceContext.at(context, pos, context.getClickedFace());
            if (!level.getBlockState(pos).canBeReplaced(cellContext)) return false;
            var placed = state.setValue(KeystoneDoorBlock.PART, part);
            if (!placed.canSurvive(level, pos) || !level.isUnobstructed(placed, pos, collision)) return false;
        }
        return true;
    }

    /** Writer injection keeps partial-write failures testable against a real level without global event hooks. */
    static boolean placeParts(BlockPlaceContext context, BlockState state, BiPredicate<BlockPos, BlockState> writer) {
        if (!canPlaceDoor(context, state)) return false;
        var level = context.getLevel(); var origin = context.getClickedPos(); var facing = state.getValue(KeystoneDoorBlock.FACING);
        var snapshots = new ArrayList<BlockSnapshot>(9);
        for (var part : KeystoneDoorPart.PLACED) snapshots.add(BlockSnapshot.create(level.dimension(), level, part.position(origin, facing), Block.UPDATE_ALL));
        int capturedBefore = level.capturedBlockSnapshots.size();
        boolean complete = false;
        try {
            for (int i = 0; i < snapshots.size(); i++) {
                var snapshot = snapshots.get(i); var placed = state.setValue(KeystoneDoorBlock.PART, KeystoneDoorPart.PLACED.get(i));
                if (level.getBlockState(snapshot.getPos()) != snapshot.getState() || !writer.test(snapshot.getPos(), placed)
                        || level.getBlockState(snapshot.getPos()) != placed) return false;
            }
            // Neighbor callbacks from a later write must not silently invalidate an earlier member.
            for (int i = 0; i < snapshots.size(); i++)
                if (level.getBlockState(snapshots.get(i).getPos()) != state.setValue(KeystoneDoorBlock.PART, KeystoneDoorPart.PLACED.get(i))) return false;
            complete = true;
            return true;
        } finally {
            if (!complete) {
                boolean capturing = level.captureBlockSnapshots, restoring = level.restoringBlockSnapshots;
                level.captureBlockSnapshots = false; level.restoringBlockSnapshots = true;
                try {
                    for (var snapshot : snapshots.reversed()) {
                        if (level.getBlockState(snapshot.getPos()) != snapshot.getState()) {
                            if (!snapshot.restore(Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE))
                                throw new IllegalStateException("Could not roll back keystone door at " + snapshot.getPos());
                        } else snapshot.restoreBlockEntity(level, snapshot.getPos());
                    }
                } finally {
                    level.captureBlockSnapshots = capturing; level.restoringBlockSnapshots = restoring;
                    level.capturedBlockSnapshots.subList(capturedBefore, level.capturedBlockSnapshots.size()).clear();
                }
            }
        }
    }
}
