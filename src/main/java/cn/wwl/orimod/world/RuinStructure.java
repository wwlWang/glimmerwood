package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public final class RuinStructure extends Structure {
    public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.STRING.fieldOf("kind").forGetter(value -> value.kind.id)
    ).apply(instance, (settings, kind) -> new RuinStructure(settings, RuinKind.byId(kind))));
    public final RuinKind kind;
    public RuinStructure(StructureSettings settings, RuinKind kind) { super(settings); this.kind = kind; }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var layout = kind.layout();
        int x = context.chunkPos().getMinBlockX(), z = context.chunkPos().getMinBlockZ();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        // Sample at four-block intervals, including the far edge, so narrow ravines and
        // water channels inside the footprint cannot pass a corner-only terrain check.
        for (int sampleX = 0; sampleX <= (layout.size().getX() + 2) / 4; sampleX++) {
            int dx = Math.min(sampleX * 4, layout.size().getX() - 1);
            for (int sampleZ = 0; sampleZ <= (layout.size().getZ() + 2) / 4; sampleZ++) {
                int dz = Math.min(sampleZ * 4, layout.size().getZ() - 1);
                int y = context.chunkGenerator().getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                min = Math.min(min, y); max = Math.max(max, y);
                if (max - min > 4 || min < 63 || max > 145) return Optional.empty();
                var column = context.chunkGenerator().getBaseColumn(x + dx, z + dz, context.heightAccessor(), context.randomState());
                if (!column.getBlock(y).getFluidState().isEmpty() || !column.getBlock(y - 1).getFluidState().isEmpty()
                        || column.getBlock(y - 1).isAir()) return Optional.empty();
            }
        }
        // Include the ground immediately outside the doorway. Surface stairs cover
        // the entire accepted four-block drop; cave stairs start at this ground height.
        int approachHeight = context.chunkGenerator().getBaseHeight(x + (int)layout.entry().x, z - 1,
                Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        for (int dx = -2; dx <= 2; dx++) {
            int approachX = x + (int)layout.entry().x + dx;
            int height = context.chunkGenerator().getBaseHeight(approachX, z - 1,
                    Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
            min = Math.min(min, height); max = Math.max(max, height);
            var column = context.chunkGenerator().getBaseColumn(approachX, z - 1, context.heightAccessor(), context.randomState());
            if (max - min > 4 || min < 63 || max > 145 || !column.getBlock(height).getFluidState().isEmpty()
                    || !column.getBlock(height - 1).getFluidState().isEmpty()) return Optional.empty();
        }
        int surface = kind == RuinKind.CAVE ? approachHeight : max;
        var origin = new BlockPos(x, surface - layout.surfaceY(), z);
        var entry = origin.offset(BlockPos.containing(layout.entry()));
        if (origin.getY() < context.heightAccessor().getMinBuildHeight() + 8
                || origin.getY() + layout.size().getY() >= context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        return Optional.of(new GenerationStub(entry, builder -> {
            builder.addPiece(new RuinPiece(context.structureTemplateManager(), kind, origin));
            builder.addPiece(new RuinApproachPiece(kind, origin));
        }));
    }
    @Override public StructureType<?> type() { return OriWorldgen.RUIN.get(); }
}
