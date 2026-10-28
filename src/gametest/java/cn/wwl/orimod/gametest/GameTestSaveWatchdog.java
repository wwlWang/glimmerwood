package cn.wwl.orimod.gametest;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Test-source only: diagnoses slow shutdown without loading chunks or terminating the server. */
@EventBusSubscriber(modid = "orimod_gametest")
public final class GameTestSaveWatchdog {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("orimod.gametest.saveWatchdog", "true"));
    private static final long DELAY_MS = 120_000;
    private static final ThreadLocal<Integer> UNLOADING = ThreadLocal.withInitial(() -> 0);
    private static final AtomicInteger LOADS_IN_UNLOAD = new AtomicInteger();
    private static Thread watcher;

    private GameTestSaveWatchdog() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void begin(ChunkEvent.Unload event) {
        if (ENABLED) UNLOADING.set(UNLOADING.get() + 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void end(ChunkEvent.Unload event) {
        if (!ENABLED) return;
        int depth = UNLOADING.get() - 1;
        if (depth == 0) UNLOADING.remove();
        else UNLOADING.set(depth);
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (ENABLED && UNLOADING.get() > 0) {
            LOADS_IN_UNLOAD.incrementAndGet();
            System.out.println("LOAD_DURING_UNLOAD " + event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (!ENABLED) return;
        System.out.println("LOAD_DURING_UNLOAD_COUNT " + LOADS_IN_UNLOAD.get());
        System.out.println("SAVE_WATCHDOG_PLAYERS " + event.getServer().getPlayerList().getPlayerCount());
        var snapshots = new ArrayList<HolderSnapshot>();
        var live = new ArrayList<LiveMap>();
        // ChunkMap's mutable maps are read ONLY on this server thread. The daemon retains
        // immutable handle lists and reads generation refs / future completion, never maps.
        for (var level : event.getServer().getAllLevels()) {
            try {
                var map = level.getChunkSource().chunkMap;
                live.add(new LiveMap(level.dimension().location().toString(), map));
                for (String name : List.of("updatingChunkMap", "pendingUnloads")) {
                    var field = ChunkMap.class.getDeclaredField(name);
                    field.setAccessible(true);
                    var handles = new ArrayList<ChunkHolder>();
                    for (var value : ((Map<?, ?>) field.get(map)).values()) handles.add((ChunkHolder) value);
                    var snapshot = new HolderSnapshot(level.dimension().location().toString(), name,
                            level.getForcedChunks().size(), List.copyOf(handles));
                    snapshots.add(snapshot);
                    System.out.println(snapshot.summary());
                }
            } catch (ReflectiveOperationException ex) {
                System.err.println("SAVE_WATCHDOG_SNAPSHOT_FAILED " + ex);
            }
        }
        var captured = List.copyOf(snapshots);
        long started = System.nanoTime();
        String run = Instant.now().toString().replace(':', '-') + "-" + ProcessHandle.current().pid();
        watcher = new Thread(() -> watch(captured, List.copyOf(live), started, run), "orimod-gametest-save-watchdog");
        watcher.setDaemon(true);
        watcher.start();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (watcher != null) watcher.interrupt();
        watcher = null;
        LOADS_IN_UNLOAD.set(0);
    }

    private static void watch(List<HolderSnapshot> snapshots, List<LiveMap> live, long started, String run) {
        int sample = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(DELAY_MS);
                var report = Path.of(System.getProperty("orimod.gametest.report", "gametest.xml")).toAbsolutePath();
                var directory = report.getParent().resolve("gametest-save-watchdog");
                Files.createDirectories(directory);
                var path = directory.resolve(run + "-" + (++sample) + ".txt");
                try (var out = new PrintWriter(Files.newBufferedWriter(path))) {
                    out.println("elapsedSeconds=" + (System.nanoTime() - started) / 1e9);
                    out.println("Holder handles were captured at ServerStoppingEvent; later-created holders are not included.");
                    out.println("forced counts are stop-entry snapshots; readiness below is sampled now.");
                    out.println("loadsDuringUnload=" + LOADS_IN_UNLOAD.get());
                    for (var snapshot : snapshots) {
                        out.println(snapshot.summary());
                        for (var holder : snapshot.holders()) {
                            if (!holder.isReadyForSaving()) describe(out, holder);
                        }
                    }
                    out.println("\nFresh published visible-map snapshots (not stop-entry handles):");
                    for (var map : live) {
                        var snapshot = map.snapshot();
                        out.println(snapshot.summary());
                        for (var holder : snapshot.holders()) if (!holder.isReadyForSaving()) describe(out, holder);
                    }
                    out.println("\nFull JVM thread stacks:");
                    for (var thread : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                        out.println("\n\"" + thread.getThreadName() + "\" id=" + thread.getThreadId()
                                + " " + thread.getThreadState() + " lock=" + thread.getLockInfo()
                                + " owner=" + thread.getLockOwnerName());
                        for (var frame : thread.getStackTrace()) out.println("    at " + frame);
                        for (var lock : thread.getLockedMonitors()) out.println("    monitor " + lock);
                        for (var lock : thread.getLockedSynchronizers()) out.println("    synchronizer " + lock);
                    }
                }
                System.err.println("SAVE_WATCHDOG_DUMP " + path);
            }
        } catch (InterruptedException expectedOnNormalStop) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            System.err.println("SAVE_WATCHDOG_FAILED " + ex);
        }
    }

    private static void describe(PrintWriter out, ChunkHolder holder) throws ReflectiveOperationException {
        var save = holder.getSaveSyncFuture();
        out.println("  " + holder.getPos() + " ticket=" + holder.getTicketLevel()
                + " refs=" + holder.getGenerationRefCount() + " saveDone=" + save.isDone()
                + " saveExceptional=" + save.isCompletedExceptionally());
        exception(out, save);
        var f = net.minecraft.server.level.GenerationChunkHolder.class.getDeclaredField("futures");
        f.setAccessible(true);
        var futures = (AtomicReferenceArray<?>) f.get(holder);
        int done = 0, pending = 0, exceptional = 0;
        for (int i = 0; i < futures.length(); i++) {
            if (!(futures.get(i) instanceof CompletableFuture<?> future)) continue;
            if (future.isDone()) done++; else {
                pending++;
                out.println("    pendingStatus=" + net.minecraft.world.level.chunk.status.ChunkStatus.getStatusList().get(i));
            }
            if (future.isCompletedExceptionally()) { exceptional++; exception(out, future); }
        }
        out.println("    generationFutures done=" + done + " pending=" + pending + " exceptional=" + exceptional);
    }

    private static void exception(PrintWriter out, CompletableFuture<?> future) {
        if (!future.isCompletedExceptionally()) return;
        try { future.join(); } catch (RuntimeException failure) { failure.printStackTrace(out); }
    }

    private record LiveMap(String dimension, ChunkMap map) {
        HolderSnapshot snapshot() throws ReflectiveOperationException {
            // promoteChunkMap publishes a CLONE through this volatile field and
            // never mutates that published map. Capture that single reference;
            // do not race the mutable updatingChunkMap/pendingUnloads maps.
            var field = ChunkMap.class.getDeclaredField("visibleChunkMap");
            field.setAccessible(true);
            var published = (Map<?, ?>) field.get(map);
            var handles = new ArrayList<ChunkHolder>();
            for (var value : published.values()) handles.add((ChunkHolder) value);
            return new HolderSnapshot(dimension, "visibleChunkMap-live", -1, List.copyOf(handles));
        }
    }

    private record HolderSnapshot(String dimension, String map, int forced, List<ChunkHolder> holders) {
        String summary() {
            return "TICKET_PROBE_STOP dimension=" + dimension + " map=" + map + " total=" + holders.size()
                    + " notReady=" + holders.stream().filter(h -> !h.isReadyForSaving()).count()
                    + " forced=" + forced;
        }
    }
}
