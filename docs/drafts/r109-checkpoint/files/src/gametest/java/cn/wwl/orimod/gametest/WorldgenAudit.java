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
        if ("shrine".equals(System.getProperty("orimod.worldgen.audit.mode"))) { shrines(event); return; }
        if (System.getProperty("orimod.worldgen.audit.mode", "").startsWith("outpost")) { outposts(event); return; }
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

    /** Normal-world outpost rejection histogram and 25 exact vanilla locate queries. */
    private static void outposts(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();var spawn=level.getSharedSpawnPos();
        var report=new LinkedHashMap<String,Object>();var rows=new ArrayList<Map<String,Object>>();
        var probes=new ArrayList<Map<String,Object>>();var histogram=new java.util.TreeMap<String,Integer>();
        report.put("seed",level.getSeed());report.put("spawn",coordinates(spawn));report.put("grid","5 x 5; 1024 blocks; centered on actual spawn; no biome filtering");
        report.put("rows",rows);report.put("probes",probes);report.put("rejections",histogram);
        var output=Path.of("outpost-audit.json");long began=System.nanoTime();
        try {
            var key=ResourceKey.create(Registries.STRUCTURE,net.minecraft.resources.ResourceLocation.parse("orimod:glades_outpost"));
            var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
            var structure=(cn.wwl.orimod.world.GladesOutpostStructure)holder.value();var source=level.getChunkSource();var generator=source.getGenerator();
            var spread=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)source.getGeneratorState().getPlacementsForStructure(holder).getFirst();
            report.put("spacing",spread.spacing());report.put("separation",spread.separation());
            for(int rz=-8;rz<=8;rz++)for(int rx=-8;rx<=8;rx++) {
                var chunk=spread.getPotentialStructureChunk(level.getSeed(),rx*spread.spacing(),rz*spread.spacing());
                var context=new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),source.randomState(),level.getStructureManager(),level.getSeed(),chunk,level,structure.biomes()::contains);
                var site=structure.preflight(context);var p=site.placement().block(cn.wwl.orimod.world.GladesOutpostPiece.ENTRY);
                var biome=generator.getBiomeSource().getNoiseBiome(p.getX()>>2,p.getY()>>2,p.getZ()>>2,source.randomState().sampler());
                String reason=structure.biomes().contains(biome)?site.reason().name():"BIOME";
                histogram.merge(reason,1,Integer::sum);probes.add(Map.of("chunk",new int[]{chunk.x,chunk.z},"reason",reason,"preflight",site.reason().name(),"biome",biome.unwrapKey().orElseThrow().location().toString()));
            }
            write(output,report);OriMod.LOGGER.info("Outpost audit rejections: {}",histogram);
            if(!System.getProperty("orimod.worldgen.audit.mode").equals("outpost_baseline")) {
                for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
                    var origin=spawn.offset(x*1024,0,z*1024);var row=new LinkedHashMap<String,Object>();row.put("origin",coordinates(origin));
                    var found=generator.findNearestMapStructure(level,HolderSet.direct(holder),origin,100,false);
                    row.put("found",found!=null);
                    if(found!=null){var p=found.getFirst();row.put("position",coordinates(p));row.put("distance",Math.hypot(p.getX()-origin.getX(),p.getZ()-origin.getZ()));
                        var cp=new ChunkPos(p);var start=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
                        if(start==null || !start.isValid())throw new IllegalStateException("No real outpost start at locate result");
                        var piece=start.getPieces().stream().filter(cn.wwl.orimod.world.GladesOutpostPiece.class::isInstance).map(cn.wwl.orimod.world.GladesOutpostPiece.class::cast).findFirst().orElseThrow();
                        var door=piece.placement().block(cn.wwl.orimod.world.GladesOutpostPiece.ENTRY);row.put("entry",coordinates(door));row.put("entryDistance",Math.hypot(door.getX()-origin.getX(),door.getZ()-origin.getZ()));
                    }
                    rows.add(row);write(output,report);OriMod.LOGGER.info("Outpost locate {}/25: {}",rows.size(),row);
                    source.tick(()->true,false);
                }
                var distances=rows.stream().mapToDouble(r->((Number)r.getOrDefault("distance",Double.POSITIVE_INFINITY)).doubleValue()).sorted().toArray();
                report.put("median",distances[12]);report.put("maximum",distances[24]);report.put("passes",distances[12]<=1100 && distances[24]<=2000);
            }
            report.put("status","complete");
        }catch(Exception ex){report.put("status","failed");report.put("error",ex.toString());OriMod.LOGGER.error("Outpost audit failed",ex);}
        finally{report.put("elapsedSeconds",(System.nanoTime()-began)/1e9);write(output,report);server.halt(false);}
    }

    private static void shrines(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();var spawn=level.getSharedSpawnPos();
        var report=new LinkedHashMap<String,Object>();var rows=new ArrayList<Map<String,Object>>();
        var probes=new ArrayList<Map<String,Object>>();var histogram=new java.util.TreeMap<String,Integer>();
        report.put("seed",level.getSeed());report.put("spawn",coordinates(spawn));report.put("grid","5 x 5; 1024 blocks; centered on actual spawn; no biome filtering");
        report.put("rows",rows);report.put("probes",probes);report.put("rejections",histogram);
        var output=Path.of("shrine-audit.json");long began=System.nanoTime();
        try {
            var key=ResourceKey.create(Registries.STRUCTURE,net.minecraft.resources.ResourceLocation.parse("orimod:glades_shrine"));
            var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
            var structure=(cn.wwl.orimod.world.SpiritShrineStructure)holder.value();var source=level.getChunkSource();var generator=source.getGenerator();
            var spread=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)source.getGeneratorState().getPlacementsForStructure(holder).getFirst();
            report.put("spacing",spread.spacing());report.put("separation",spread.separation());
            for(int rz=-8;rz<=8;rz++)for(int rx=-8;rx<=8;rx++) {
                var chunk=spread.getPotentialStructureChunk(level.getSeed(),rx*spread.spacing(),rz*spread.spacing());
                var context=new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),source.randomState(),level.getStructureManager(),level.getSeed(),chunk,level,structure.biomes()::contains);
                var site=structure.preflight(context);var p=site.placement().block(cn.wwl.orimod.world.SpiritShrinePiece.ENTRY);
                var biome=generator.getBiomeSource().getNoiseBiome(p.getX()>>2,p.getY()>>2,p.getZ()>>2,source.randomState().sampler());
                String reason=structure.biomes().contains(biome)?site.reason().name():"BIOME";
                histogram.merge(reason,1,Integer::sum);probes.add(Map.of("chunk",new int[]{chunk.x,chunk.z},"reason",reason,"preflight",site.reason().name(),"biome",biome.unwrapKey().orElseThrow().location().toString()));
            }
            write(output,report);OriMod.LOGGER.info("Shrine audit rejections: {}",histogram);
            if(!System.getProperty("orimod.worldgen.audit.mode").equals("shrine_baseline")) {
                for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
                    var origin=spawn.offset(x*1024,0,z*1024);var row=new LinkedHashMap<String,Object>();row.put("origin",coordinates(origin));
                    var found=generator.findNearestMapStructure(level,HolderSet.direct(holder),origin,100,false);
                    row.put("found",found!=null);
                    if(found!=null){var p=found.getFirst();row.put("position",coordinates(p));row.put("distance",Math.hypot(p.getX()-origin.getX(),p.getZ()-origin.getZ()));
                        var cp=new ChunkPos(p);var start=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
                        if(start==null || !start.isValid())throw new IllegalStateException("No real shrine start at locate result");
                        var piece=start.getPieces().stream().filter(cn.wwl.orimod.world.SpiritShrinePiece.class::isInstance).map(cn.wwl.orimod.world.SpiritShrinePiece.class::cast).findFirst().orElseThrow();
                        var door=piece.placement().block(cn.wwl.orimod.world.SpiritShrinePiece.ENTRY);row.put("entry",coordinates(door));row.put("entryDistance",Math.hypot(door.getX()-origin.getX(),door.getZ()-origin.getZ()));
                    }
                    rows.add(row);write(output,report);OriMod.LOGGER.info("Shrine locate {}/25: {}",rows.size(),row);
                    source.tick(()->true,false);
                }
                var distances=rows.stream().mapToDouble(r->((Number)r.getOrDefault("distance",Double.POSITIVE_INFINITY)).doubleValue()).sorted().toArray();
                report.put("median",distances[12]);report.put("maximum",distances[24]);report.put("passes",distances[12]<=1500);
            }
            report.put("status","complete");
        }catch(Exception ex){report.put("status","failed");report.put("error",ex.toString());OriMod.LOGGER.error("Shrine audit failed",ex);}
        finally{report.put("elapsedSeconds",(System.nanoTime()-began)/1e9);write(output,report);server.halt(false);}
    }

    private static int[] coordinates(BlockPos pos) { return new int[]{pos.getX(), pos.getY(), pos.getZ()}; }
    private static void write(Path path, Map<String, Object> data) {
        try { Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n"); }
        catch (Exception error) { throw new IllegalStateException("Cannot write worldgen audit", error); }
    }
}
