package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.block.SpiritVineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import java.util.*;

/** R100: rare spirit tree. JSON rarity 1/220 chunks means 56,320 blocks²/attempt (~237-block grid spacing);
 * LOCAL_MODIFICATIONS precedes vanilla vegetation in this chunk (neighbors may already be decorated).
 * Terrain/obstruction rejection further reduces actual density. All horizontal writes stay within ±14. */
public final class SpiritTreeFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MAX_RELIEF = 3;
    public SpiritTreeFeature() { super(NoneFeatureConfiguration.CODEC); }

    public static boolean terrainAllowed(int[] heights, boolean dryAndSupported) {
        if (!dryAndSupported || heights.length != 25) return false;
        var stats = Arrays.stream(heights).summaryStatistics();
        return (long) stats.getMax() - stats.getMin() <= MAX_RELIEF;
    }

    public static boolean replaceable(boolean air, boolean treeReplaceable, boolean leaves, boolean snow, boolean fluid, boolean blockEntity) {
        return !fluid && !blockEntity && (air || treeReplaceable || leaves || snow);
    }

    public static boolean replaceable(BlockState state) {
        return replaceable(state.isAir(), state.is(BlockTags.REPLACEABLE_BY_TREES), state.is(BlockTags.LEAVES),
                state.is(Blocks.SNOW), !state.getFluidState().isEmpty(), state.hasBlockEntity());
    }

    public enum Reason {
        RUIN_GUARD, BLIGHT_GUARD, RELIEF, NOT_DRY, FOOT_UNGROUNDED, FOOT_BLOCKED,
        CLEARANCE_LOG, CLEARANCE_OTHER, BUILD_HEIGHT, OK
    }

    /** No world writes, tickets or generation. Failure plans deliberately expose no partial writes. */
    public record Preflight(Reason reason, BlockPos base, BlockPos rejectedAt, int minGroundY, int maxGroundY,
                            Map<BlockPos, BlockState> writes) {
        private static Preflight rejected(Reason reason, BlockPos base, BlockPos at, int minY, int maxY) {
            return new Preflight(reason, base, at, minY, maxY, Map.of());
        }
    }

    /** For observations: decline the entire footprint before any heightmap/block reads if a chunk is absent. */
    public static Optional<Preflight> preflightLoaded(ServerLevel level, BlockPos origin) {
        int radius = SpiritTreeShape.RADIUS;
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++)
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return Optional.empty();
        return Optional.of(preflight(level, origin));
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var check = preflight(context.level(), context.origin());
        if (check.reason() != Reason.OK) return false;
        for (var entry : check.writes().entrySet()) context.level().setBlock(entry.getKey(), entry.getValue(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return true;
    }

    /** Same ordered predicates as placement. NOT_DRY also includes an unsupported/log/leaf/entity floor;
     * BUILD_HEIGHT includes WorldGenRegion write bounds. ServerLevel intentionally has no ruin guard. */
    public static Preflight preflight(WorldGenLevel level, BlockPos origin) {
        if (RuinVegetationGuard.blocksTree(level, origin, SpiritTreeShape.RADIUS))
            return Preflight.rejected(Reason.RUIN_GUARD, origin, origin, origin.getY(), origin.getY());
        int[] heights = new int[25];
        BlockPos badFloor = null;
        int index = 0;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX() + x, origin.getZ() + z);
            heights[index++] = y;
            var floor = new BlockPos(origin.getX() + x, y - 1, origin.getZ() + z);
            if (!supported(level, floor) && badFloor == null) badFloor = floor;
        }
        var stats = Arrays.stream(heights).summaryStatistics();
        int minY = stats.getMin(), maxY = stats.getMax();
        var base = new BlockPos(origin.getX(), maxY, origin.getZ());
        // Preserve terrainAllowed's dry-first short circuit when both checks fail.
        if (badFloor != null) return Preflight.rejected(Reason.NOT_DRY, base, badFloor, minY, maxY);
        if (!terrainAllowed(heights, true)) return Preflight.rejected(Reason.RELIEF, base, base, minY, maxY);
        if (BlightedGroundsFeature.nearBlock(level, base, 12, OriBlocks.GLOOM_CORE.get()))
            return Preflight.rejected(Reason.BLIGHT_GUARD, base, base, minY, maxY);
        var plan = SpiritTreeShape.plan(base);
        var writes = new LinkedHashMap<BlockPos, BlockState>();
        for (var entry : plan.blocks().entrySet()) writes.put(base.offset(entry.getKey()), state(entry.getValue()));
        // Ground the buttresses and the entrance on uneven terrain, without replacing solid soil.
        var feet = new HashSet<BlockPos>();
        for (var entry : plan.blocks().entrySet()) if (entry.getKey().getY() == 0 && entry.getValue() != SpiritTreeShape.Part.AIR)
            feet.add(base.offset(entry.getKey()).below());
        for (int x = -1; x <= 1; x++) for (int z = -5; z <= -3; z++) feet.add(base.offset(x, -1, z));
        for (var foot : feet) {
            boolean grounded = false;
            for (int depth = 0; depth <= SpiritTreeShape.MAX_ROOT_DROP; depth++) {
                var p = foot.below(depth);
                if (supported(level, p)) { grounded = true; break; }
                if (!replaceable(level.getBlockState(p))) return Preflight.rejected(Reason.FOOT_BLOCKED, base, p, minY, maxY);
                writes.put(p, OriBlocks.SPIRIT_LOG.get().defaultBlockState());
            }
            if (!grounded) return Preflight.rejected(Reason.FOOT_UNGROUNDED, base, foot, minY, maxY);
        }
        // Include crown holes: a stone pillar cannot hide inside the hollow or a decorative notch.
        var clearance = new HashSet<>(writes.keySet());
        for (var offset : plan.crownSpace()) clearance.add(base.offset(offset));
        // Preflight includes the alcove air, supports, fluids, block entities, build height and feature write bounds.
        for (var pos : clearance) {
            if (level.isOutsideBuildHeight(pos) || !level.ensureCanWrite(pos))
                return Preflight.rejected(Reason.BUILD_HEIGHT, base, pos, minY, maxY);
            var existing = level.getBlockState(pos);
            if (!replaceable(existing)) return Preflight.rejected(existing.is(BlockTags.LOGS)
                    ? Reason.CLEARANCE_LOG : Reason.CLEARANCE_OTHER, base, pos, minY, maxY);
        }
        return new Preflight(Reason.OK, base, null, minY, maxY, Collections.unmodifiableMap(writes));
    }

    private static boolean supported(WorldGenLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.hasBlockEntity() && !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.LEAVES) && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static BlockState state(SpiritTreeShape.Part part) {
        return switch (part) {
            case LOG_X, LOG_Y, LOG_Z -> OriBlocks.SPIRIT_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                    part == SpiritTreeShape.Part.LOG_X ? Direction.Axis.X : part == SpiritTreeShape.Part.LOG_Z ? Direction.Axis.Z : Direction.Axis.Y);
            case LEAVES -> OriBlocks.SPIRIT_LEAVES.get().defaultBlockState();
            case VINE, VINE_TIP -> OriBlocks.SPIRIT_VINE.get().defaultBlockState().setValue(SpiritVineBlock.TIP, part == SpiritTreeShape.Part.VINE_TIP);
            case HEART -> OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
        };
    }
}
