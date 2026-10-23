package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * R114: spirit trees and blighted grounds were biome-modifier features, so {@code /locate structure} could never find
 * them. They are now structure starts (one per random_spread cell) whose single piece runs the unchanged feature in its
 * origin chunk. The start is a noise-only prefilter; the feature keeps its full real-block preflight.
 */
public final class FeatureSiteStructure extends Structure {
    public enum Site {
        // Mirrors each feature's own terrain rule: tree = 5x5 relief <= 3; blight = soil disc up to r13, every column
        // within 3 of the centre and relief <= 6 (an r6 prefilter left phantom blight starts on forest slopes).
        SPIRIT_TREE(OriWorldgen.SPIRIT_TREE::get, 2, SpiritTreeFeature.MAX_RELIEF, SpiritTreeFeature.MAX_RELIEF),
        BLIGHTED_GROUNDS(OriWorldgen.BLIGHTED_GROUNDS::get, 12, 6, 3);
        /** Both features keep every write within this horizontal radius of their origin. */
        public static final int RADIUS = 14;
        private final Supplier<? extends Feature<NoneFeatureConfiguration>> feature;
        /** Radius of the noise sample disc, largest relief across it and largest deviation from the centre column. */
        public final int sampleRadius, maxRelief, maxDeviation;
        Site(Supplier<? extends Feature<NoneFeatureConfiguration>> feature, int sampleRadius, int maxRelief, int maxDeviation) {
            this.feature = feature; this.sampleRadius = sampleRadius; this.maxRelief = maxRelief; this.maxDeviation = maxDeviation;
        }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        /** The feature's own preflight verdict at {@code at}, without writing (for retries and the decline log). */
        public String declineReason(net.minecraft.world.level.WorldGenLevel level, net.minecraft.core.BlockPos at, long seed) {
            if (this == SPIRIT_TREE) {
                var check = SpiritTreeFeature.preflight(level, at);
                return check.reason().name() + (check.rejectedAt() == null ? "" : " at " + check.rejectedAt().toShortString());
            }
            var check = BlightedGroundsFeature.preflight(level, at, seed);
            return check.reason().name() + (check.detail().isEmpty() ? "" : " (" + check.detail() + ")");
        }
        public Feature<NoneFeatureConfiguration> feature() { return feature.get(); }
        public static Site byId(String id) {
            for (var site : values()) if (site.id().equals(id)) return site;
            throw new IllegalArgumentException("Unknown feature site " + id);
        }
    }

    public static final MapCodec<FeatureSiteStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.STRING.fieldOf("site").forGetter(value -> value.site.id())
    ).apply(instance, (settings, site) -> new FeatureSiteStructure(settings, Site.byId(site))));
    public final Site site;
    public FeatureSiteStructure(StructureSettings settings, Site site) { super(settings); this.site = site; }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Keep the origin RADIUS-compatible: anywhere in the chunk keeps all writes inside its 3x3 neighbourhood.
        var random = context.random();
        int x = context.chunkPos().getMinBlockX() + random.nextInt(16), z = context.chunkPos().getMinBlockZ() + random.nextInt(16);
        int y = height(context, x, z);
        if (!RuinSiteRules.surfaceInRange(y)) return Optional.empty();
        if (!context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                QuartPos.fromBlock(y), QuartPos.fromBlock(z), context.randomState().sampler()))) return Optional.empty();
        // Coarse grid first (most sites fail there), then the fine 3-block grid: the old 6-block step alone let a
        // 4-block bump between samples through (phantom start at -190,34). Coarse points are not sampled twice.
        int r = site.sampleRadius, fine = Math.max(1, r / 4), coarse = Math.max(fine, r / 2);
        int[] range = {y, y};
        // Water surfaces count as height in WORLD_SURFACE_WG: keep every sample at least 2 above sea level, so shores and
        // swamp pools (the features refuse wet floors) don't leave located but empty starts.
        int dry = context.chunkGenerator().getSeaLevel() + 2;
        if (y < dry) return Optional.empty();
        for (int step : fine == coarse ? new int[]{fine} : new int[]{coarse, fine})
            for (int dx = -r; dx <= r; dx += step) for (int dz = -r; dz <= r; dz += step) {
                if (dx * dx + dz * dz > r * r || step == fine && step != coarse && dx % coarse == 0 && dz % coarse == 0) continue;
                int h = height(context, x + dx, z + dz);
                range[0] = Math.min(range[0], h); range[1] = Math.max(range[1], h);
                if (h < dry || range[1] - range[0] > site.maxRelief || Math.abs(h - y) > site.maxDeviation) return Optional.empty();
            }
        var column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
        if (!column.getBlock(y).getFluidState().isEmpty() || !column.getBlock(y - 1).getFluidState().isEmpty()
                || column.getBlock(y - 1).isAir()) return Optional.empty();
        var origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new FeatureSitePiece(site, origin))));
    }

    private static int height(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
    }

    @Override public StructureType<?> type() { return OriWorldgen.FEATURE_SITE.get(); }
}
