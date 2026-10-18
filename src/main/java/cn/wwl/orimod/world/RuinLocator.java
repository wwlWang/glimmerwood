package cn.wwl.orimod.world;

import cn.wwl.orimod.network.ProgressReplyPayload;
import cn.wwl.orimod.network.RuinGuidePayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded search of actual structure starts. Already-cleared sites are skipped per player. */
public final class RuinLocator {
    public static final int RANGE = 2048, MAX_CANDIDATES = 2048, MAX_PENDING = 4;
    static final int MAX_ENTRY_OFFSET = 64;
    private static final RandomSpreadStructurePlacement LEGACY_PLACEMENT = new RandomSpreadStructurePlacement(16, 6, RandomSpreadType.LINEAR, 19721014);
    private static final Map<UUID, Search> SEARCHES = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final TicketType<UUID> SEARCH_TICKET = TicketType.create("orimod_ruin_search", UUID::compareTo, 40);
    private static final int SEARCH_DISTANCE = ChunkLevel.byStatus(ChunkStatus.FULL) - ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);
    private RuinLocator() {}

    public static void request(ServerPlayer player) {
        if (!SpiritData.isAwakened(player) || !player.isAlive()) return;
        long now = player.server.getTickCount();
        if (SEARCHES.containsKey(player.getUUID())) { reply(player, "guide.orimod.searching"); return; }
        if (COOLDOWNS.getOrDefault(player.getUUID(), 0L) > now) { reply(player, "guide.orimod.cooldown"); return; }
        COOLDOWNS.put(player.getUUID(), now + 200);
        var level = player.serverLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var placements = new HashSet<RandomSpreadStructurePlacement>();
        for (var kind : RuinKind.values()) {
            var holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, kind.location()));
            if (holder.isEmpty()) continue;
            for (var placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()))
                if (placement instanceof RandomSpreadStructurePlacement spread) placements.add(spread);
        }
        var origin = player.blockPosition();
        var candidates = candidates(placements, level.getSeed(), origin);
        if (candidates.positions().isEmpty()) { reply(player, "guide.orimod.no_ruins"); return; }
        SEARCHES.put(player.getUUID(), new Search(level, origin, candidates, now));
        reply(player, "guide.orimod.searching");
    }

    static Candidates candidates(Collection<RandomSpreadStructurePlacement> current, long seed, BlockPos origin) {
        if (current.isEmpty()) return new Candidates(List.of(), false);
        var placements = new HashSet<>(current);
        // Existing worlds keep starts generated with the original spacing. Search those chunks too.
        placements.add(LEGACY_PLACEMENT);
        var candidates = new HashSet<ChunkPos>();
        var originChunk = new ChunkPos(origin);
        int candidateRange = RANGE + MAX_ENTRY_OFFSET;
        for (var spread : placements) {
            int regionX = Math.floorDiv(originChunk.x, spread.spacing()), regionZ = Math.floorDiv(originChunk.z, spread.spacing());
            int radius = candidateRange / (16 * spread.spacing()) + 1;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                var candidate = spread.getPotentialStructureChunk(seed, (regionX + x) * spread.spacing(), (regionZ + z) * spread.spacing());
                if (horizontalDistance(origin, candidate.getWorldPosition()) <= (double)candidateRange * candidateRange) candidates.add(candidate);
            }
        }
        var sorted = candidates.stream().sorted(Comparator.<ChunkPos>comparingDouble(c -> horizontalDistance(origin, c.getWorldPosition()))
                .thenComparingInt(c -> c.x).thenComparingInt(c -> c.z)).limit(MAX_CANDIDATES).toList();
        return new Candidates(sorted, candidates.size() > MAX_CANDIDATES);
    }
    record Candidates(List<ChunkPos> positions, boolean truncated) {}

    private static double horizontalDistance(BlockPos a, BlockPos b) { double x = a.getX() - b.getX(), z = a.getZ() - b.getZ(); return x * x + z * z; }
    static boolean couldHaveCloserEntry(BlockPos origin, ChunkPos candidate, BlockPos bestEntry) {
        double limit = Math.sqrt(horizontalDistance(origin, bestEntry)) + MAX_ENTRY_OFFSET;
        return horizontalDistance(origin, candidate.getWorldPosition()) <= limit * limit;
    }
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var search = SEARCHES.get(player.getUUID());
        if (search == null) return;
        if (player.serverLevel() != search.level || !player.isAlive()) { stop(player.getUUID()); return; }
        if (player.server.getTickCount() - search.started > 60 * 20) { stop(player.getUUID()); reply(player, "guide.orimod.search_timeout"); return; }
        // Keep at most four STRUCTURE_STARTS tickets alive. Candidate chunks are ordered,
        // but rotated entrances can change which ruin is nearest to the player.
        for (var pending : search.pending)
            search.level.getChunkSource().addRegionTicket(SEARCH_TICKET, pending.chunk(), SEARCH_DISTANCE, player.getUUID());
        while (!search.pending.isEmpty()) {
            if (finishIfNearest(player, search)) return;
            if (!search.pending.getFirst().future().isDone()) break;
            var pending = search.pending.removeFirst();
            ChunkAccess chunk = null;
            try { chunk = pending.future().join().orElse(null); }
            catch (java.util.concurrent.CompletionException error) { cn.wwl.orimod.OriMod.LOGGER.warn("Ruin search chunk failed", error); }
            search.level.getChunkSource().removeRegionTicket(SEARCH_TICKET, pending.chunk(), SEARCH_DISTANCE, player.getUUID());
            search.checked++;
            if (chunk == null) search.failedChunks++;
            if (chunk != null) for (var start : chunk.getAllStarts().values()) {
                if (!start.isValid() || !(start.getStructure() instanceof RuinStructure structure)) continue;
                for (var piece : start.getPieces()) if (piece instanceof RuinPiece ruin) {
                    var shrine = ruin.placement().block(structure.kind.layout().shrine());
                    var key = ChallengeSession.key(search.level.dimension(), shrine);
                    if (player.getData(SpiritData.PROGRESS).rewards().getOrDefault("clear/" + key, 0) > 0) continue;
                    var entry = BlockPos.containing(ruin.placement().point(structure.kind.layout().entry()));
                    double distance = horizontalDistance(search.origin, entry);
                    if (distance > RANGE * RANGE) continue;
                    if (search.best == null || distance < horizontalDistance(search.origin, search.best.entry()))
                        search.best = new Found(structure.kind, entry);
                }
            }
        }
        if (finishIfNearest(player, search)) return;
        if (search.index >= search.candidates.size() && search.pending.isEmpty()) {
            stop(player.getUUID());
            reply(player, search.failedChunks == 0 && !search.truncated ? "guide.orimod.no_ruins" : "guide.orimod.search_timeout");
            return;
        }
        while (search.pending.size() < MAX_PENDING && search.index < search.candidates.size()) {
            var candidate = search.candidates.get(search.index++);
            search.level.getChunkSource().addRegionTicket(SEARCH_TICKET, candidate, SEARCH_DISTANCE, player.getUUID());
            // The off-thread API schedules onto the server and returns a future without blocking this tick.
            var future = CompletableFuture.supplyAsync(() -> search.closed
                    ? CompletableFuture.<ChunkResult<ChunkAccess>>completedFuture(ChunkResult.error("Search closed"))
                    : search.level.getChunkSource().getChunkFuture(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true)).thenCompose(result -> result);
            search.pending.addLast(new Pending(candidate, future));
        }
    }
    private static boolean finishIfNearest(ServerPlayer player, Search search) {
        if (search.best == null) return false;
        var next = !search.pending.isEmpty() ? search.pending.getFirst().chunk()
                : search.index < search.candidates.size() ? search.candidates.get(search.index) : null;
        if (next != null && couldHaveCloserEntry(search.origin, next, search.best.entry())) return false;
        var found = search.best;
        stop(player.getUUID());
        PacketDistributor.sendToPlayer(player, new RuinGuidePayload(search.level.dimension().location().toString(), found.entry(), found.kind().id));
        reply(player, "guide.orimod.found", found.kind().title(), found.entry().getX(), found.entry().getY(), found.entry().getZ());
        return true;
    }
    private static void reply(ServerPlayer player, String key, Object... args) {
        var message = Component.translatable(key, args);
        PacketDistributor.sendToPlayer(player, new ProgressReplyPayload(message, false));
        player.displayClientMessage(message, false);
    }
    private static void stop(UUID owner) {
        var search = SEARCHES.remove(owner);
        if (search != null) close(search, owner);
    }
    private static void close(Search search, UUID owner) {
        search.closed = true;
        for (var pending : search.pending) {
            pending.future().cancel(false);
            if (owner != null) search.level.getChunkSource().removeRegionTicket(SEARCH_TICKET, pending.chunk(), SEARCH_DISTANCE, owner);
        }
        search.pending.clear();
    }
    public static void stopAll() {
        for (var owner : List.copyOf(SEARCHES.keySet())) stop(owner);
        COOLDOWNS.clear();
    }
    public static void discardAll() {
        SEARCHES.values().forEach(search -> close(search, null));
        SEARCHES.clear(); COOLDOWNS.clear();
    }
    public static Map<String, Integer> lifecycleState() {
        return Map.of("searches", SEARCHES.size(), "cooldowns", COOLDOWNS.size(),
                "tickets", SEARCHES.values().stream().mapToInt(search -> search.pending.size()).sum());
    }
    public static Map<String, Object> debug(ServerPlayer player) {
        var search = SEARCHES.get(player.getUUID());
        return search == null ? Map.of("active", false) : Map.of("active", true, "checked", search.checked, "scheduled", search.index,
                "candidates", search.candidates.size(), "failedChunks", search.failedChunks, "ticket", !search.pending.isEmpty(), "inFlight", search.pending.size());
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { stop(event.getEntity().getUUID()); COOLDOWNS.remove(event.getEntity().getUUID()); }
    private record Pending(ChunkPos chunk, CompletableFuture<ChunkResult<ChunkAccess>> future) {}
    private record Found(RuinKind kind, BlockPos entry) {}
    private static final class Search {
        final ServerLevel level;
        final BlockPos origin;
        final List<ChunkPos> candidates;
        final long started;
        final boolean truncated;
        int index, checked, failedChunks;
        Found best;
        volatile boolean closed;
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        Search(ServerLevel level, BlockPos origin, Candidates candidates, long started) {
            this.level = level; this.origin = origin; this.candidates = candidates.positions(); this.truncated = candidates.truncated(); this.started = started;
        }
    }
}
