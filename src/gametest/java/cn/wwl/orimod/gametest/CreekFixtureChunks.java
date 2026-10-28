package cn.wwl.orimod.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Fixture lifetime, not a change to production chunk scheduling. Server thread only. */
final class CreekFixtureChunks {
    private static final Field UPDATING = field("updatingChunkMap");
    private static final Field UNLOADING = field("pendingUnloads");
    private static final long TIMEOUT_NANOS = 30_000_000_000L;

    private CreekFixtureChunks() {}

    /** Forced chunks also start neighbouring generation. A FULL getChunk() only waits
     * for that chunk's future, not those neighbours or the task's releaseClaim(). */
    static void release(ServerLevel level, List<ChunkPos> owned) {
        if (owned.isEmpty()) return;
        try {
            settle(level, "before-release");
        } finally {
            for (var pos : owned) level.setChunkForced(pos.x, pos.z, false);
            owned.clear();
        }
        // Ticket downgrade cancels tasks asynchronously. Drain their normal main-
        // thread continuations before another neighbouring fixture can reuse a
        // holder which still has an unload callback queued for its former lease.
        settle(level, "after-release");
    }

    static void settle(ServerLevel level, String phase) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Fixture drain off server thread");
        var source = level.getChunkSource();
        long start = System.nanoTime();
        int initial = -1, max = 0, polls = 0, stable = 0;
        List<ChunkHolder> outstanding = List.of();
        while (stable < 2) {
            // pollTask runs distance updates, submits generation and services FULL
            // conversion / light continuations; it does not process unloadQueue.
            boolean work = source.pollTask();
            polls++;
            outstanding = outstanding(source.chunkMap);
            if (initial < 0) initial = outstanding.size();
            max = Math.max(max, outstanding.size());
            stable = outstanding.isEmpty() && !work ? stable + 1 : 0;
            if (System.nanoTime() - start > TIMEOUT_NANOS) {
                throw new IllegalStateException("Creek fixture generation did not settle " + phase + ": "
                        + outstanding.stream().limit(12).map(h -> h.getPos() + " refs="
                        + h.getGenerationRefCount() + " save=" + h.getSaveSyncFuture()).toList());
            }
            if (!work && stable < 2) LockSupport.parkNanos(100_000);
        }
        System.out.println("CREEK_FIXTURE_DRAIN phase=" + phase + " initial=" + initial + " max=" + max
                + " final=" + outstanding.size() + " polls=" + polls + " ms=" + (System.nanoTime() - start) / 1e6);
    }

    private static List<ChunkHolder> outstanding(ChunkMap map) {
        var seen = new IdentityHashMap<ChunkHolder, Boolean>();
        var result = new ArrayList<ChunkHolder>();
        try {
            for (var f : List.of(UPDATING, UNLOADING)) {
                for (Object v : ((Map<?, ?>) f.get(map)).values()) {
                    var holder = (ChunkHolder) v;
                    if (seen.put(holder, true) == null && !holder.isReadyForSaving()) result.add(holder);
                }
            }
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        return result;
    }

    private static Field field(String name) {
        try { var f = ChunkMap.class.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
}
