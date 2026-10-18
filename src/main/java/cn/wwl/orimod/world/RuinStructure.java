package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public final class RuinStructure extends Structure {
    public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.STRING.fieldOf("kind").forGetter(value -> value.kind.id)
    ).apply(instance, (settings, kind) -> new RuinStructure(settings, RuinKind.byId(kind))));
    public final RuinKind kind;
    public RuinStructure(StructureSettings settings, RuinKind kind) { super(settings); this.kind = kind; }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var layout = kind.layout();
        int x = context.chunkPos().getMinBlockX(), z = context.chunkPos().getMinBlockZ();
        var rotation = Rotation.getRandom(context.random());
        var footprint = RuinPlacement.atMinimum(kind, new BlockPos(x, 0, z), rotation);
        var entryColumn = BlockPos.containing(footprint.point(layout.entry()));
        // Reject impossible biome columns before expensive noise sampling. Check every possible
        // final entry quart-height, so this cannot reject a start the vanilla biome check accepts.
        int entryDelta = (int)Math.floor(layout.entry().y) - layout.surfaceY();
        boolean possibleBiome = false;
        for (int y = QuartPos.fromBlock(63 + entryDelta); y <= QuartPos.fromBlock(145 + entryDelta); y++) {
            if (context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(entryColumn.getX()), y,
                    QuartPos.fromBlock(entryColumn.getZ()), context.randomState().sampler()))) { possibleBiome = true; break; }
        }
        if (!possibleBiome) return Optional.empty();
        var samples = new TerrainSamples(context);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        var footprintPoints = new LinkedHashSet<BlockPos>();
        int farX = layout.size().getX() - 1, farZ = layout.size().getZ() - 1;
        // Corners reject broad slopes early; retain every original four-block grid sample.
        for (int dx : new int[]{0, farX}) for (int dz : new int[]{0, farZ}) footprintPoints.add(footprint.block(new BlockPos(dx, 0, dz)));
        for (int sampleX = 0; sampleX <= (layout.size().getX() + 2) / 4; sampleX++)
            for (int sampleZ = 0; sampleZ <= (layout.size().getZ() + 2) / 4; sampleZ++)
                footprintPoints.add(footprint.block(new BlockPos(Math.min(sampleX * 4, farX), 0, Math.min(sampleZ * 4, farZ))));
        for (var sample : footprintPoints) {
            int y = samples.height(sample);
            min = Math.min(min, y); max = Math.max(max, y);
            if (max - min > 4 || min < 63 || max > 145) return Optional.empty();
        }
        // Include the ground immediately outside the doorway. Surface stairs cover
        // the entire accepted four-block drop; cave stairs start at this ground height.
        var approach = RuinApproachPiece.doorway(kind, footprint);
        int approachHeight = samples.height(approach.block(new BlockPos(0, 0, -1)));
        for (int dx = -2; dx <= 2; dx++) {
            var sample = approach.block(new BlockPos(dx, 0, -1));
            int height = samples.height(sample);
            min = Math.min(min, height); max = Math.max(max, height);
            if (max - min > 4 || min < 63 || max > 145) return Optional.empty();
        }
        int surface = kind == RuinKind.CAVE ? approachHeight : max;
        var origin = footprint.origin().atY(surface - layout.surfaceY());
        var placement = new RuinPlacement(origin, rotation);
        var doorway = RuinApproachPiece.doorway(kind, placement);
        int[] terrain = new int[RuinApproachPiece.WIDTH * RuinApproachPiece.DEPTH];
        for (int dx = -2; dx <= 2; dx++) for (int dz = -9; dz <= -1; dz++) {
            var sample = doorway.block(new BlockPos(dx, 0, dz));
            int height = samples.height(sample);
            if (height < 63 || height > 145 || Math.abs(height - doorway.origin().getY()) > 7) return Optional.empty();
            terrain[(dx + 2) * RuinApproachPiece.DEPTH + dz + 9] = height;
        }
        var entry = BlockPos.containing(placement.point(layout.entry()));
        if (!context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(entry.getX()), QuartPos.fromBlock(entry.getY()),
                QuartPos.fromBlock(entry.getZ()), context.randomState().sampler()))) return Optional.empty();
        if (!samples.dry()) return Optional.empty();
        if (origin.getY() < context.heightAccessor().getMinBuildHeight() + 8
                || origin.getY() + layout.size().getY() >= context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        return Optional.of(new GenerationStub(entry, builder -> {
            builder.addPiece(new RuinPiece(context.structureTemplateManager(), kind, placement));
            builder.addPiece(new RuinApproachPiece(doorway, terrain));
        }));
    }
    private static final class TerrainSamples {
        private final GenerationContext context;
        private final LinkedHashMap<BlockPos, Integer> heights = new LinkedHashMap<>();
        TerrainSamples(GenerationContext context) { this.context = context; }
        int height(BlockPos pos) {
            return heights.computeIfAbsent(pos.atY(0), key -> context.chunkGenerator().getBaseHeight(key.getX(), key.getZ(),
                    Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()));
        }
        boolean dry() {
            // Only viable terrain/biomes need full columns. A lookup's slope rejection never
            // constructs them, and duplicate doorway/footprint samples are read just once.
            for (var entry : heights.entrySet()) {
                var pos = entry.getKey(); int y = entry.getValue();
                var column = context.chunkGenerator().getBaseColumn(pos.getX(), pos.getZ(), context.heightAccessor(), context.randomState());
                if (!column.getBlock(y).getFluidState().isEmpty() || !column.getBlock(y - 1).getFluidState().isEmpty()
                        || column.getBlock(y - 1).isAir()) return false;
            }
            return true;
        }
    }
    @Override public StructureType<?> type() { return OriWorldgen.RUIN.get(); }
}
