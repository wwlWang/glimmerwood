package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.combat.Flash;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.*;

/** Loaded-section palette prefilter; cache positions, never the answer for a particular spawn point.
 * One shared cache serves lamps, tree hearts and outpost wells. Placement/removal invalidates the owning section. */
public final class GloomTideDefense {
    public static final int CACHE_TICKS = 100, MAX_SECTIONS = 4096;
    private record Sites(long at, List<BlockPos> lamps, List<BlockPos> hearts, List<BlockPos> wells) {}
    private static final Map<ServerLevel, LinkedHashMap<Long, Sites>> CACHE = new IdentityHashMap<>();

    @FunctionalInterface public interface Query { boolean protects(ServerLevel level, Vec3 point); }
    public static final Query QUERY = GloomTideDefense::protects;
    public static boolean protects(ServerLevel level, Vec3 point) {
        return BlightData.get(level).protects(point) || Flash.protectsSpawn(level, point, GloomTideRules.FLASH_RADIUS) || blocksProtect(level, point);
    }
    public static boolean blocksProtect(ServerLevel level, Vec3 point) {
        int r = Math.max(GloomTideRules.TREE_RADIUS, GloomTideRules.LANTERN_RADIUS);
        var min = BlockPos.containing(point.subtract(r, r, r));
        var max = BlockPos.containing(point.add(r, r, r));
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue; // Never cache absence of an unloaded chunk or load it.
                for (int sy = Math.max(min.getY(), level.getMinBuildHeight()) >> 4;
                     sy <= Math.min(max.getY(), level.getMaxBuildHeight() - 1) >> 4; sy++) {
                    var sites = sites(level, chunk, cx, sy, cz);
                    for (var p : sites.lamps) if (GloomTideRules.within(p.distToCenterSqr(point), GloomTideRules.LANTERN_RADIUS)) return true;
                    for (var p : sites.wells) if (GloomTideRules.within(p.distToCenterSqr(point), GloomTideRules.OUTPOST_RADIUS)) return true;
                    for (var p : sites.hearts) if (GloomTideRules.within(p.distToCenterSqr(point), GloomTideRules.TREE_RADIUS)) return true;
                }
            }
        return false;
    }
    private static Sites sites(ServerLevel level, LevelChunk chunk, int cx, int sy, int cz) {
        var cache = CACHE.computeIfAbsent(level, l -> new LinkedHashMap<>(256, .75f, true));
        long key = SectionPos.asLong(cx, sy, cz), now = level.getGameTime();
        var saved = cache.get(key);
        if (saved != null && now >= saved.at && now - saved.at < CACHE_TICKS) return saved;
        var lamps = new ArrayList<BlockPos>(); var hearts = new ArrayList<BlockPos>(); var wells = new ArrayList<BlockPos>();
        var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
        if (section.maybeHas(s -> s.is(OriBlocks.BASH_ANCHOR) || s.is(OriBlocks.SPIRIT_TREE_HEART) || s.is(OriBlocks.GLADES_WELL)))
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                var state = section.getBlockState(x, y, z);
                if (state.is(OriBlocks.BASH_ANCHOR)) lamps.add(new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
                else if (state.is(OriBlocks.GLADES_WELL)) wells.add(new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
                else if (state.is(OriBlocks.SPIRIT_TREE_HEART)) hearts.add(new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
            }
        saved = new Sites(now, List.copyOf(lamps), List.copyOf(hearts), List.copyOf(wells));
        cache.put(key, saved);
        while (cache.size() > MAX_SECTIONS) cache.remove(cache.keySet().iterator().next());
        return saved;
    }
    /** R104: reuse the same palette/cache for actual light sources; callers check lamp LIT themselves. */
    public static List<BlockPos> lightSources(ServerLevel level, Vec3 point, int radius) {
        var out = new ArrayList<BlockPos>();
        var min = BlockPos.containing(point.subtract(radius,radius,radius));
        var max = BlockPos.containing(point.add(radius,radius,radius));
        for (int cx=min.getX()>>4; cx<=max.getX()>>4; cx++) for (int cz=min.getZ()>>4; cz<=max.getZ()>>4; cz++) {
            var chunk=level.getChunkSource().getChunkNow(cx,cz); if (chunk==null) continue;
            for (int sy=Math.max(min.getY(),level.getMinBuildHeight())>>4; sy<=Math.min(max.getY(),level.getMaxBuildHeight()-1)>>4; sy++) {
                var s=sites(level,chunk,cx,sy,cz); out.addAll(s.lamps); out.addAll(s.hearts);
            }
        }
        return out;
    }
    public static void invalidate(ServerLevel level, BlockPos pos) {
        var cache = CACHE.get(level);
        if (cache != null) cache.remove(SectionPos.asLong(pos));
    }
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var cache = CACHE.get(level); if (cache == null) return;
        var pos = event.getChunk().getPos();
        cache.keySet().removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z);
    }
    public static void clear(ServerLevel level) { CACHE.remove(level); }
    public static void reset() { CACHE.clear(); }
    private GloomTideDefense() {}
}
