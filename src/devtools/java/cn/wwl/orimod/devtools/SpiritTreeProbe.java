package cn.wwl.orimod.devtools;

import cn.wwl.orimod.world.SpiritTreeFeature;
import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.world.SpiritTreeSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Devtools-only observation; never places a feature, changes tickets, or loads a missing chunk. */
final class SpiritTreeProbe {
    private SpiritTreeProbe() {}

    static Map<String, Object> scan(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int step) {
        if (step < 1 || step > 1024 || minX > maxX || minZ > maxZ
                || minX < -29_999_970 || maxX > 29_999_970 || minZ < -29_999_970 || maxZ > 29_999_970
                || (long) maxX - minX > 4095 || (long) maxZ - minZ > 4095
                || ((long) maxX - minX) / step + 1 > 1024
                || (((long) maxX - minX) / step + 1) * (((long) maxZ - minZ) / step + 1) > 1024)
            throw new IllegalArgumentException("Ordered world coordinates, spans <= 4096, step 1..1024 and at most 1024 samples required");
        var rows = new ArrayList<Map<String, Object>>();
        var counts = new TreeMap<String, Integer>();
        var eligibleCounts = new TreeMap<String, Integer>();
        var byBiome = new TreeMap<String, Map<String, Integer>>();
        var treeBiomes = TagKey.create(Registries.BIOME, ResourceLocation.parse("orimod:has_spirit_tree"));
        int chunksBefore = level.getChunkSource().getLoadedChunksCount();
        long started = System.nanoTime();
        for (int x = minX; x <= maxX; x += step) for (int z = minZ; z <= maxZ; z += step) {
            var row = new LinkedHashMap<String, Object>(); row.put("x", x); row.put("z", z);
            var checked = SpiritTreeFeature.preflightLoaded(level, new BlockPos(x, 0, z));
            if (checked.isEmpty()) {
                row.put("reason", "UNLOADED"); counts.merge("UNLOADED", 1, Integer::sum); rows.add(row); continue;
            }
            var check = checked.orElseThrow();
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var biome = level.getBiome(new BlockPos(x, surfaceY, z));
            String biomeId = biome.unwrapKey().orElseThrow().location().toString();
            boolean eligible = biome.is(treeBiomes);
            String reason = check.reason().name();
            row.put("reason", reason); row.put("biome", biomeId); row.put("eligibleBiome", eligible);
            row.put("base", xyz(check.base())); row.put("groundMinY", check.minGroundY()); row.put("groundMaxY", check.maxGroundY());
            row.put("plannedWrites", check.writes().size());
            if (check.rejectedAt() != null) {
                row.put("rejectedAt", xyz(check.rejectedAt()));
                row.put("block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(check.rejectedAt()).getBlock()).toString());
            }
            // Existing manual samples can move the heightmap into their own crown. Mark them, never erase them.
            SpiritTreeSearch.nearest(level, Vec3.atCenterOf(check.base()), 32)
                    .ifPresent(pos -> row.put("existingHeart", xyz(pos)));
            counts.merge(reason, 1, Integer::sum);
            if (eligible) eligibleCounts.merge(reason, 1, Integer::sum);
            byBiome.computeIfAbsent(biomeId, ignored -> new TreeMap<>()).merge(reason, 1, Integer::sum);
            rows.add(row);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("context", "Loaded ServerLevel, same as /place feature; RUIN_GUARD is generation-only and not evaluated here");
        result.put("dimension", level.dimension().location().toString()); result.put("seed", level.getSeed());
        result.put("grid", Map.of("minX", minX, "maxX", maxX, "minZ", minZ, "maxZ", maxZ, "step", step));
        result.put("hearts", hearts(level, minX, maxX, minZ, maxZ));
        result.put("counts", counts); result.put("eligibleCounts", eligibleCounts); result.put("byBiome", byBiome); result.put("rows", rows);
        result.put("loadedChunksBefore", chunksBefore); result.put("loadedChunksAfter", level.getChunkSource().getLoadedChunksCount());
        result.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000.0);
        return result;
    }

    /** Exact census for the requested XZ rectangle, full build height; no sampling gaps or chunk loads. */
    private static List<List<Integer>> hearts(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        var found = new ArrayList<List<Integer>>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (int sy = level.getMinSection(); sy < level.getMaxSection(); sy++) {
                var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                if (!section.maybeHas(state -> state.is(OriBlocks.SPIRIT_TREE_HEART))) continue;
                for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++)
                    for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++)
                        for (int y = 0; y < 16; y++) if (section.getBlockState(x & 15, y, z & 15).is(OriBlocks.SPIRIT_TREE_HEART))
                            found.add(List.of(x, (sy << 4) + y, z));
            }
        }
        return found;
    }

    private static List<Integer> xyz(BlockPos pos) { return List.of(pos.getX(), pos.getY(), pos.getZ()); }
}
