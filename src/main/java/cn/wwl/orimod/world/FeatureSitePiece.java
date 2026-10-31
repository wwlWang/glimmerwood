package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Optional;

/** Runs the saved authoritative plan once in its origin chunk. Legacy NBT retains strict feature retries. */
public final class FeatureSitePiece extends StructurePiece {
    private final FeatureSiteStructure.Site site;
    private final BlockPos origin;
    private int placementVersion;
    private long placementSeed;
    private FeatureSitePlan plan;
    private String refusal = "";

    public FeatureSitePiece(FeatureSiteStructure.Site site, BlockPos origin) {
        super(OriWorldgen.FEATURE_SITE_PIECE.get(), 0, bounds(origin));
        this.site = site; this.origin = origin.immutable();
    }

    FeatureSitePiece(FeatureSiteStructure.Site site,BlockPos origin,long seed,FeatureSitePlan plan){
        this(site,origin);this.placementVersion=1;this.placementSeed=seed;this.plan=plan;
        int bottom=Math.min(origin.getY()-1,plan.writes().keySet().stream().mapToInt(BlockPos::getY).min().orElse(origin.getY()))
                -FeatureSitePlan.MAX_SUPPORT_EXTENSION;
        this.boundingBox=new BoundingBox(origin.getX()-14,bottom,origin.getZ()-14,
                origin.getX()+14,origin.getY()+SpiritTreeShape.MAX_HEIGHT+8,origin.getZ()+14);
    }

    public FeatureSitePiece(CompoundTag tag) {
        super(OriWorldgen.FEATURE_SITE_PIECE.get(), tag);
        this.site = FeatureSiteStructure.Site.byId(tag.getString("Site"));
        this.origin = BlockPos.of(tag.getLong("Origin"));
        this.placementVersion=tag.getInt("PlacementVersion");this.placementSeed=tag.getLong("PlacementSeed");
    }

    private static BoundingBox bounds(BlockPos origin) {
        int r = FeatureSiteStructure.Site.RADIUS;
        return new BoundingBox(origin.getX() - r, origin.getY() - 8, origin.getZ() - r,
                origin.getX() + r, origin.getY() + SpiritTreeShape.MAX_HEIGHT + 8, origin.getZ() + r);
    }

    public FeatureSiteStructure.Site site() { return site; }
    public BlockPos origin() { return origin; }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Site", site.id()); tag.putLong("Origin", origin.asLong());
        if(placementVersion>0){tag.putInt("PlacementVersion",placementVersion);tag.putLong("PlacementSeed",placementSeed);}
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        if (!chunk.equals(new ChunkPos(origin))) return;
        if(placementVersion>0){
            long seed=placementSeed;var at=origin;
            if (place(level, generator, at, seed)) return;
            com.mojang.logging.LogUtils.getLogger().warn("orimod {} registered start at {} could not place its accepted plan: {}",site.id(),origin,refusal);
            return;
        }
        var reasons = new java.util.ArrayList<String>();
        String reason = "";
        for (var at : candidates(origin)) {
            // One shape seed per candidate, shared by placement and its diagnosis, so the logged reason is the real one.
            long seed = random.nextLong();
            if (place(level, generator, at, seed)) return;
            reason = site.declineReason(level, at, RandomSource.create(seed).nextLong());
            // Vegetation can hide the ground (a trunk raises the heightmap) or block a write. Ruins, other sites,
            // build height and unloaded neighbours cannot be cleared, so no clearing is cut for them.
            if (vegetationMayCause(reason.split(" ")[0]) && clearVanillaVegetation(level, at, FeatureSiteStructure.Site.RADIUS) > 0) {
                if (place(level, generator, at, seed)) return;
                reason = site.declineReason(level, at, RandomSource.create(seed).nextLong());
            }
            reasons.add(at.toShortString() + ": " + reason);
        }
        // A located start with no content; logged so acceptance runs can count phantoms and see why.
        com.mojang.logging.LogUtils.getLogger().warn("orimod {} legacy start at {} declined by its feature preflight: {}", site.id(), origin, reasons);
    }

    /** The feature draws its shape seed as the first nextLong() of this source (see declineReason). */
    private boolean place(WorldGenLevel level, ChunkGenerator generator, BlockPos at, long seed) {
        if(placementVersion>0){
            if(plan==null)plan=FeatureSitePlan.check(site,new FeatureTerrain.Noise(generator,level.getLevel().getChunkSource().randomState(),level,origin),origin,seed);
            refusal=plan.place(level,site);return refusal.equals("OK");
        }
        return site.feature().place(new FeaturePlaceContext<>(Optional.empty(), level, generator, RandomSource.create(seed), at,
                NoneFeatureConfiguration.INSTANCE));
    }

    /** Preflight reasons that clearing trees and ground plants can resolve; guards, build height and loading cannot. */
    static boolean vegetationMayCause(String reason) {
        return switch (reason) {
            case "RUIN_GUARD", "BLIGHT_GUARD", "BUILD_HEIGHT", "OK", "RUIN", "SANCTUARY", "UNLOADED" -> false;
            default -> true;
        };
    }

    /** The noise origin, then the chunk's quadrant centres nearest first. Every candidate stays in the origin chunk,
     * so writes still reach only its 3x3 neighbourhood. */
    static java.util.List<BlockPos> candidates(BlockPos origin) {
        int minX = origin.getX() & ~15, minZ = origin.getZ() & ~15;
        var list = new java.util.ArrayList<BlockPos>();
        list.add(origin);
        java.util.List<BlockPos> quadrants = new java.util.ArrayList<>();
        for (int qx : new int[]{4, 12}) for (int qz : new int[]{4, 12}) {
            var at = new BlockPos(minX + qx, origin.getY(), minZ + qz);
            if (at.distManhattan(origin) > 3) quadrants.add(at);
        }
        quadrants.sort(java.util.Comparator.comparingInt(at -> at.distManhattan(origin)));
        list.addAll(quadrants.subList(0, Math.min(2, quadrants.size())));
        return list;
    }

    /** Trees and the ground plants that block a write: flowers, mushrooms (huge ones too), bushes, saplings, roots. */
    static boolean vegetation(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.VINE) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.BEE_NEST) || state.is(Blocks.COCOA);
    }

    /** Removes non-Ori trees and ground plants above the ground within {@code radius}; returns the number of blocks cleared. */
    static int clearVanillaVegetation(WorldGenLevel level, BlockPos origin, int radius) {
        int cleared = 0;
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++)
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                if ((x - origin.getX()) * (x - origin.getX()) + (z - origin.getZ()) * (z - origin.getZ()) > radius * radius) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = top; y >= origin.getY() - 6; y--) {
                    var pos = new BlockPos(x, y, z);
                    if (!level.ensureCanWrite(pos)) break;
                    var state = level.getBlockState(pos);
                    boolean ori = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("orimod");
                    if (!ori && vegetation(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS); cleared++;
                    } else if (!state.isAir() && !state.is(BlockTags.REPLACEABLE_BY_TREES)) break;
                }
            }
        return cleared;
    }
}
