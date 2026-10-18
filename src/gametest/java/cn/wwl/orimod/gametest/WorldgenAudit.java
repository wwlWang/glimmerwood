package cn.wwl.orimod.gametest;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.world.RuinKind;
import cn.wwl.orimod.world.RuinPiece;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normal noise-generated worlds and the exact /locate structure query, never a flat GameTest level. */
final class WorldgenAudit {
    private WorldgenAudit() {}

    static void started(ServerStartedEvent event) {
        if ("site".equals(System.getProperty("orimod.worldgen.audit.mode"))) { sites(event); return; }
        var server = event.getServer();
        var level = server.overworld();
        var spawn = level.getSharedSpawnPos();
        var rows = new ArrayList<Map<String, Object>>();
        var report = new LinkedHashMap<String, Object>();
        report.put("seed", level.getSeed());
        report.put("spawn", coordinates(spawn));
        report.put("grid", "5 x 5; 1024 blocks; centered on actual world spawn; includes water and all biomes");
        report.put("query", "ChunkGenerator.findNearestMapStructure(level, single kind, origin, 100, false), same as /locate structure");
        report.put("coverageRadius", 1500);
        report.put("rows", rows);
        var output = Path.of("worldgen-audit.json");
        long started = System.nanoTime();
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
                var origin = spawn.offset(x * 1024, 0, z * 1024);
                for (var kind : RuinKind.values()) {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("kind", kind.id);
                    row.put("origin", coordinates(origin));
                    long before = System.nanoTime();
                    var holder = registry.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, kind.location()));
                    var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), origin, 100, false);
                    row.put("milliseconds", (System.nanoTime() - before) / 1_000_000);
                    row.put("found", found != null);
                    if (found != null) {
                        row.put("position", coordinates(found.getFirst()));
                        double dx = found.getFirst().getX() - origin.getX(), dz = found.getFirst().getZ() - origin.getZ();
                        double distance = Math.sqrt(dx * dx + dz * dz);
                        row.put("distance", distance);
                        row.put("within1500", distance <= 1500);
                        var chunkPos = new ChunkPos(found.getFirst());
                        var chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
                        var start = chunk.getStartForStructure(holder.value());
                        if (start == null || !start.isValid()) throw new IllegalStateException("Locate returned no real structure start");
                        var piece = start.getPieces().stream().filter(RuinPiece.class::isInstance).map(RuinPiece.class::cast).findFirst().orElseThrow();
                        row.put("templateOrigin", coordinates(piece.origin()));
                        row.put("rotation", piece.getRotation().name());
                        var entry = BlockPos.containing(piece.placement().point(kind.layout().entry()));
                        row.put("entry", coordinates(entry));
                        double ex = entry.getX() - origin.getX(), ez = entry.getZ() - origin.getZ();
                        row.put("entryDistance", Math.sqrt(ex * ex + ez * ez));
                    } else row.put("within1500", false);
                    rows.add(row);
                    write(output, report);
                    OriMod.LOGGER.info("Worldgen audit seed={} query={}/75 kind={} distance={} elapsed={}ms", level.getSeed(), rows.size(), kind.id, row.get("distance"), row.get("milliseconds"));
                }
            }
            report.put("status", "complete");
        } catch (Exception error) {
            report.put("status", "failed");
            report.put("error", error.toString());
            OriMod.LOGGER.error("Worldgen audit failed", error);
        } finally {
            report.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
            write(output, report);
            server.halt(false);
        }
    }

    /**
     * WORLD02: locate real starts on a coarse grid, generate their chunks to FULL (with one chunk of
     * neighbours so trees and fluids settle), then inspect the placed result with RuinSiteInspector.
     */
    static void sites(ServerStartedEvent event) {
        var server = event.getServer();
        var level = server.overworld();
        var spawn = level.getSharedSpawnPos();
        var rows = new ArrayList<Map<String, Object>>();
        var report = new LinkedHashMap<String, Object>();
        report.put("seed", level.getSeed());
        report.put("mode", "site");
        report.put("grid", "4 x 4; 1024 blocks; centered on world spawn; each start inspected once");
        report.put("rows", rows);
        var output = Path.of("ruin-sites.json");
        var seen = new java.util.HashSet<String>();
        long started = System.nanoTime();
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (int z = -2; z <= 1; z++) for (int x = -2; x <= 1; x++) for (var kind : RuinKind.values()) {
                var origin = spawn.offset(x * 1024 + 512, 0, z * 1024 + 512);
                var holder = registry.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, kind.location()));
                var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), origin, 100, false);
                if (found == null) continue;
                var chunkPos = new ChunkPos(found.getFirst());
                var start = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
                if (start == null || !start.isValid()) throw new IllegalStateException("Locate returned no real structure start");
                if (!seen.add(kind.id + chunkPos)) continue;
                var bounds = start.getBoundingBox();
                for (int cx = (bounds.minX() >> 4) - 1; cx <= (bounds.maxX() >> 4) + 1; cx++)
                    for (int cz = (bounds.minZ() >> 4) - 1; cz <= (bounds.maxZ() >> 4) + 1; cz++) level.getChunk(cx, cz);
                var piece = start.getPieces().stream().filter(RuinPiece.class::isInstance).map(RuinPiece.class::cast).findFirst().orElseThrow();
                var result = cn.wwl.orimod.world.RuinSiteInspector.inspect(level, kind, piece.placement());
                var row = new LinkedHashMap<String, Object>();
                row.put("kind", kind.id);
                row.put("origin", coordinates(piece.origin()));
                row.put("rotation", piece.getRotation().name());
                var entry = BlockPos.containing(piece.placement().point(kind.layout().entry()));
                row.put("biome", level.getBiome(entry).unwrapKey().map(key -> key.location().toString()).orElse("?"));
                row.putAll(result.metrics());
                row.put("issues", result.issues());
                rows.add(row);
                write(output, report);
                OriMod.LOGGER.info("Ruin site audit seed={} n={} kind={} issues={}", level.getSeed(), rows.size(), kind.id, result.issues());
                // Keep memory bounded: let the generated area unload before the next start.
                level.getChunkSource().tick(() -> true, false);
            }
            report.put("status", "complete");
        } catch (Exception error) {
            report.put("status", "failed");
            report.put("error", error.toString());
            OriMod.LOGGER.error("Ruin site audit failed", error);
        } finally {
            report.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
            write(output, report);
            server.halt(false);
        }
    }

    private static int[] coordinates(BlockPos pos) { return new int[]{pos.getX(), pos.getY(), pos.getZ()}; }
    private static void write(Path path, Map<String, Object> data) {
        try { Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n"); }
        catch (Exception error) { throw new IllegalStateException("Cannot write worldgen audit", error); }
    }
}
