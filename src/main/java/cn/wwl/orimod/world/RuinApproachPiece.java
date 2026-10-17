package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** A short generated path joins the doorway to actual terrain after carving and surface placement. */
public final class RuinApproachPiece extends StructurePiece {
    private final int centerX, frontZ, doorwayGround;

    public RuinApproachPiece(RuinKind kind, BlockPos origin) {
        super(OriWorldgen.APPROACH.get(), 0, bounds(kind, origin));
        centerX = origin.getX() + (int)kind.layout().entry().x;
        frontZ = origin.getZ() - 8;
        doorwayGround = origin.getY() + (kind == RuinKind.CAVE ? kind.layout().surfaceY() : 1);
    }

    public RuinApproachPiece(CompoundTag tag) {
        super(OriWorldgen.APPROACH.get(), tag);
        centerX = tag.getInt("CenterX"); frontZ = tag.getInt("FrontZ"); doorwayGround = tag.getInt("DoorwayGround");
    }

    private static BoundingBox bounds(RuinKind kind, BlockPos origin) {
        int x = origin.getX() + (int)kind.layout().entry().x;
        return new BoundingBox(x - 2, origin.getY() - 16, origin.getZ() - 9,
                x + 2, origin.getY() + kind.layout().surfaceY() + 64, origin.getZ() - 1);
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CenterX", centerX); tag.putInt("FrontZ", frontZ); tag.putInt("DoorwayGround", doorwayGround);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        // The eight-block approach lies in the preceding chunk; read its untouched
        // front edge before placing any path blocks. Logs/leaves are not ground.
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            int ground = naturalGround(level, x, frontZ - 1);
            // Keep the natural landing just outside the paving, but remove low
            // branches so players can walk onto the path from ground level.
            clearAbove(level, chunkBounds, x, frontZ - 1, ground - 1, ground + 2);
            clearCanopy(level, chunkBounds, x, frontZ - 1, ground + 3, boundingBox.maxY());
            for (int i = 0; i < 8; i++) {
                int floor = ground - 1 + Math.round((doorwayGround - ground) * i / 7.0f);
                var pos = new BlockPos(x, floor, frontZ + i);
                if (!chunkBounds.isInside(pos)) continue;
                int oldGround = naturalGround(level, x, frontZ + i);
                int previous = i == 0 ? ground - 1 : ground - 1 + Math.round((doorwayGround - ground) * (i - 1) / 7.0f);
                int next = i == 7 ? doorwayGround - 1 : ground - 1 + Math.round((doorwayGround - ground) * (i + 1) / 7.0f);
                BlockState surface = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                if (floor > previous) surface = Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
                else if (floor > next) surface = Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
                level.setBlock(pos, surface, 2);
                // Cut through the old surface too; a descending path must not
                // leave a floating dirt roof above its three-block clearance.
                clearAbove(level, chunkBounds, x, frontZ + i, floor, Math.max(floor + 3, oldGround + 2));
                clearCanopy(level, chunkBounds, x, frontZ + i, Math.max(floor + 3, oldGround + 2) + 1, boundingBox.maxY());
                for (int dy = 1; dy <= 16; dy++) {
                    var below = pos.below(dy);
                    if (level.getBlockState(below).isCollisionShapeFullBlock(level, below)) break;
                    level.setBlock(below, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void clearAbove(WorldGenLevel level, BoundingBox chunkBounds, int x, int z, int floor, int top) {
        for (int y = floor + 1; y <= top; y++) {
            var pos = new BlockPos(x, y, z);
            if (chunkBounds.isInside(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    static void clearCanopy(WorldGenLevel level, BoundingBox chunkBounds, int x, int z, int from, int top) {
        // WORLD_SURFACE_WG can still describe the pre-vegetation surface here.
        // Scan the bounded tree-height allowance instead of trusting that map.
        for (int y = from; y <= Math.min(top, level.getMaxBuildHeight() - 1); y++) {
            var pos = new BlockPos(x, y, z); var state = level.getBlockState(pos);
            if (chunkBounds.isInside(pos) && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static int naturalGround(WorldGenLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        while (y > level.getMinBuildHeight()) {
            var pos = new BlockPos(x, y, z); var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && state.isCollisionShapeFullBlock(level, pos)) return y + 1;
            y--;
        }
        return y + 1;
    }
}
