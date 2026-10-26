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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The approach shape comes from saved terrain samples, independent of neighboring chunk decoration. */
public final class RuinApproachPiece extends StructurePiece implements net.neoforged.neoforge.common.world.PieceBeardifierModifier {
    public static final int WIDTH = 5, DEPTH = 9;
    private final RuinPlacement placement;
    private final int[] terrain;
    private final boolean carvedEntrance;

    public RuinApproachPiece(RuinPlacement doorway, int[] terrain) {
        this(doorway,terrain,false);
    }
    public RuinApproachPiece(RuinPlacement doorway, int[] terrain, boolean carvedEntrance) {
        super(OriWorldgen.APPROACH.get(), 0, bounds(doorway,carvedEntrance));
        if (terrain.length != WIDTH * DEPTH) throw new IllegalArgumentException("Approach needs 45 terrain samples");
        this.placement = doorway;
        this.terrain = terrain.clone();
        this.carvedEntrance = carvedEntrance;
    }

    public RuinApproachPiece(CompoundTag tag) {
        super(OriWorldgen.APPROACH.get(), tag);
        if (tag.contains("DoorX")) {
            placement = new RuinPlacement(new BlockPos(tag.getInt("DoorX"), tag.getInt("DoorY"), tag.getInt("DoorZ")),
                    RuinPlacement.readRotation(tag.getString("Rot")));
        } else {
            placement = new RuinPlacement(new BlockPos(tag.getInt("CenterX"), tag.getInt("DoorwayGround"), tag.getInt("FrontZ") + 8), Rotation.NONE);
        }
        int[] saved = tag.getIntArray("Terrain");
        // Old starts are completed using the same seed's base terrain, never mutable neighbor blocks.
        terrain = saved.length == WIDTH * DEPTH ? saved : new int[0];
        carvedEntrance=tag.getBoolean("CarvedEntrance"); // Missing = unchanged legacy approach.
    }

    public static RuinPlacement doorway(RuinKind kind, RuinPlacement ruin) {
        return doorway(kind, kind.layout(), ruin);
    }
    public static RuinPlacement doorway(RuinKind kind, RuinKind.Layout layout, RuinPlacement ruin) {
        return new RuinPlacement(ruin.block(new BlockPos((int)layout.entry().x,
                layout.approachY(), 0)), ruin.rotation());
    }

    /** The stairs adapt to the sampled terrain themselves; a beard on this tall box would hollow the ground under it. */
    @Override public BoundingBox getBeardifierBox() { return boundingBox; }
    @Override public net.minecraft.world.level.levelgen.structure.TerrainAdjustment getTerrainAdjustment() {
        return net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE;
    }
    @Override public int getGroundLevelDelta() { return 0; }

    private static BoundingBox bounds(RuinPlacement doorway, boolean carvedEntrance) {
        int radius=carvedEntrance?4:2;
        return doorway.blocks(new BlockPos(-radius, -24, -9), new BlockPos(radius, 64, -1));
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("DoorX", placement.origin().getX()); tag.putInt("DoorY", placement.origin().getY()); tag.putInt("DoorZ", placement.origin().getZ());
        tag.putString("Rot", placement.rotation().name()); tag.putIntArray("Terrain", terrain);
        if(carvedEntrance)tag.putBoolean("CarvedEntrance",true);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        for (int dx = -2; dx <= 2; dx++) {
            int ground = height(level, generator, dx, -9);
            var landing = placement.block(new BlockPos(dx, 0, -9));
            clearAbove(level, chunkBounds, landing.getX(), landing.getZ(), ground - 1, ground + 2);
            // Carvers cut caves and ravines after the noise samples; bridge a hole under the landing
            // so the first step never towers over the real ground (R16 site audit: landing:step).
            support(level, chunkBounds, landing.atY(ground - 1), true);
            clearCanopy(level, chunkBounds, landing.getX(), landing.getZ(), ground + 3, boundingBox.maxY());
            for (int i = 0; i < 8; i++) {
                int floor = floor(ground, i);
                var column = placement.block(new BlockPos(dx, 0, -8 + i));
                var pos = new BlockPos(column.getX(), floor, column.getZ());
                if (!chunkBounds.isInside(pos)) continue;
                int oldGround = height(level, generator, dx, -8 + i);
                int previous = i == 0 ? ground - 1 : floor(ground, i - 1);
                int next = i == 7 ? placement.origin().getY() - 1 : floor(ground, i + 1);
                BlockState surface = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                if (floor > previous) surface = Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, placement.rotation().rotate(Direction.SOUTH));
                else if (floor > next) surface = Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, placement.rotation().rotate(Direction.NORTH));
                level.setBlock(pos, surface, 2);
                int top = Math.max(floor + 3, oldGround + 2);
                clearAbove(level, chunkBounds, pos.getX(), pos.getZ(), floor, top);
                clearCanopy(level, chunkBounds, pos.getX(), pos.getZ(), top + 1, boundingBox.maxY());
                support(level, chunkBounds, pos, false);
            }
        }
        if(carvedEntrance)dressPortal(level,generator,chunkBounds);
    }

    /** Only the new cave's saved entrance gets a stone frontage. The adapted stair geometry is unchanged. */
    private void dressPortal(WorldGenLevel level,ChunkGenerator generator,BoundingBox chunkBounds){
        int ground=height(level,generator,0,-9),base=floor(ground,1);
        for(int z=-8;z<=-1;z++)for(int x:new int[]{-3,3}){
            int localGround=height(level,generator,x<0?-2:2,z);
            int low=z==-1?placement.origin().getY()-1:floor(ground,z+8);
            for(int y=low;y<localGround;y++)portalPut(level,chunkBounds,x,y,z,
                    (y+z)%5==0?Blocks.MOSSY_STONE_BRICKS.defaultBlockState():Blocks.TUFF_BRICKS.defaultBlockState());
        }
        for(int z=-7;z<=-6;z++){
            for(int x:new int[]{-3,3})for(int y=base;y<=base+5;y++)
                portalPut(level,chunkBounds,x,y,z,(y==base+3?Blocks.CHISELED_TUFF_BRICKS:Blocks.TUFF_BRICKS).defaultBlockState());
            for(int x=-2;x<=2;x++)portalPut(level,chunkBounds,x,base+6-(Math.abs(x)==2?1:0),z,
                    (x==0?Blocks.CHISELED_DEEPSLATE:Blocks.TUFF_BRICKS).defaultBlockState());
        }
        for(int x:new int[]{-2,2})for(int z=-7;z<=-6;z++)portalPut(level,chunkBounds,x,base+6,z,Blocks.TUFF_BRICKS.defaultBlockState());
        for(int x:new int[]{-4,4})support(level,chunkBounds,placement.block(new BlockPos(x,0,x<0?-5:-8)).atY(base),false);
        portalPut(level,chunkBounds,0,base+5,-7,cn.wwl.orimod.block.OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState()
                .setValue(cn.wwl.orimod.block.GreatSpiritRootBlock.LIGHT,cn.wwl.orimod.block.GreatSpiritRootBlock.Light.KNOT));
        for(int x:new int[]{-4,4})for(int y=base;y<=base+(x<0?2:4);y++)
            portalPut(level,chunkBounds,x,y,x<0?-5:-8,(y==base?Blocks.MOSSY_STONE_BRICKS:Blocks.CHISELED_TUFF_BRICKS).defaultBlockState());
    }
    private void portalPut(WorldGenLevel level,BoundingBox bounds,int x,int y,int z,BlockState state){
        var p=placement.block(new BlockPos(x,0,z)).atY(y);
        if(bounds.isInside(p))level.setBlock(p,state.rotate(placement.rotation()),2);
    }

    private int floor(int ground, int step) { return RuinSiteRules.stepFloor(ground, placement.origin().getY(), step); }

    /** Fills a column under {@code top} (and {@code top} itself when asked) down to the first full block. */
    private static void support(WorldGenLevel level, BoundingBox chunkBounds, BlockPos top, boolean includeTop) {
        for (int dy = includeTop ? 0 : 1; dy <= RuinSiteRules.MAX_FILL; dy++) {
            var below = top.below(dy);
            if (!chunkBounds.isInside(below) || below.getY() < level.getMinBuildHeight()) break;
            if (level.getBlockState(below).isCollisionShapeFullBlock(level, below)) break;
            level.setBlock(below, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
        }
    }

    private int height(WorldGenLevel level, ChunkGenerator generator, int x, int z) {
        if (terrain.length > 0) return terrain[(x + 2) * DEPTH + z + 9];
        var column = placement.block(new BlockPos(x, 0, z));
        return generator.getBaseHeight(column.getX(), column.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                level, level.getLevel().getChunkSource().randomState());
    }

    private static void clearAbove(WorldGenLevel level, BoundingBox chunkBounds, int x, int z, int floor, int top) {
        for (int y = floor + 1; y <= Math.min(top, level.getMaxBuildHeight() - 1); y++) {
            var pos = new BlockPos(x, y, z);
            if (chunkBounds.isInside(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    static void clearCanopy(WorldGenLevel level, BoundingBox chunkBounds, int x, int z, int from, int top) {
        for (int y = from; y <= Math.min(top, level.getMaxBuildHeight() - 1); y++) {
            var pos = new BlockPos(x, y, z);
            if (!chunkBounds.isInside(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
