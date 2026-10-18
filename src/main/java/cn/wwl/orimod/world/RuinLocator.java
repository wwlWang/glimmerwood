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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded search of actual structure starts. Already-cleared sites are skipped per player. */
public final class RuinLocator {
    public static final int RANGE = 2048, MAX_CANDIDATES = 256;
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
        var candidates = new HashSet<ChunkPos>();
        for (var kind : RuinKind.values()) {
            var holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, kind.location()));
            if (holder.isEmpty()) continue;
            for (var placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get())) {
                if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
                int regionX = Math.floorDiv(player.chunkPosition().x, spread.spacing());
                int regionZ = Math.floorDiv(player.chunkPosition().z, spread.spacing());
                int radius = RANGE / (16 * spread.spacing()) + 1;
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                    var candidate = spread.getPotentialStructureChunk(level.getSeed(), (regionX + x) * spread.spacing(), (regionZ + z) * spread.spacing());
                    double dx = candidate.getMinBlockX() - player.getX(), dz = candidate.getMinBlockZ() - player.getZ();
                    if (dx * dx + dz * dz <= RANGE * RANGE) candidates.add(candidate);
                }
            }
        }
        var origin = player.blockPosition();
        var sorted = candidates.stream().sorted(Comparator.comparingDouble(c -> horizontalDistance(origin, c.getWorldPosition()))).limit(MAX_CANDIDATES).toList();
        if (sorted.isEmpty()) { reply(player, "guide.orimod.no_ruins"); return; }
        SEARCHES.put(player.getUUID(), new Search(level, origin, sorted, now));
        reply(player, "guide.orimod.searching");
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) { double x = a.getX() - b.getX(), z = a.getZ() - b.getZ(); return x * x + z * z; }
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var search = SEARCHES.get(player.getUUID());
        if (search == null) return;
        if (player.serverLevel() != search.level || !player.isAlive()) { stop(player.getUUID()); return; }
        if (player.server.getTickCount() - search.started > 30 * 20) { stop(player.getUUID()); reply(player, "guide.orimod.search_timeout"); return; }
        if (search.pending != null) {
            // Vanilla's UNKNOWN ticket expires after one tick. Keep the current asynchronous
            // generation request alive until its result is inspected, without loading full terrain.
            search.level.getChunkSource().addRegionTicket(SEARCH_TICKET, search.ticket, SEARCH_DISTANCE, player.getUUID());
            if (!search.pending.isDone()) return;
            ChunkAccess chunk = null;
            try { chunk = search.pending.join().orElse(null); }
            catch (java.util.concurrent.CompletionException error) { cn.wwl.orimod.OriMod.LOGGER.warn("Ruin search chunk failed", error); }
            search.pending = null;
            releaseTicket(search, player.getUUID());
            if (chunk == null) search.failedChunks++;
            if (chunk != null) for (var start : chunk.getAllStarts().values()) {
                if (!start.isValid() || !(start.getStructure() instanceof RuinStructure structure)) continue;
                for (var piece : start.getPieces()) if (piece instanceof RuinPiece ruin) {
                    var shrine = ruin.origin().offset(structure.kind.layout().shrine());
                    var key = ChallengeSession.key(search.level.dimension(), shrine);
                    if (player.getData(SpiritData.PROGRESS).rewards().getOrDefault("clear/" + key, 0) > 0) continue;
                    var entry = ruin.origin().offset(BlockPos.containing(structure.kind.layout().entry()));
                    if (horizontalDistance(search.origin, entry) > RANGE * RANGE) continue;
                    stop(player.getUUID());
                    PacketDistributor.sendToPlayer(player, new RuinGuidePayload(search.level.dimension().location().toString(), entry, structure.kind.id));
                    reply(player, "guide.orimod.found", structure.kind.title(), entry.getX(), entry.getY(), entry.getZ());
                    return;
                }
            }
        }
        if (search.index >= search.candidates.size()) { stop(player.getUUID()); reply(player, search.failedChunks == 0 ? "guide.orimod.no_ruins" : "guide.orimod.search_timeout"); return; }
        var candidate = search.candidates.get(search.index++);
        search.ticket = candidate;
        search.level.getChunkSource().addRegionTicket(SEARCH_TICKET, candidate, SEARCH_DISTANCE, player.getUUID());
        // This vanilla API blocks when called on the server thread. Its off-thread branch safely
        // schedules the request on that thread and returns a future; inspect results only here.
        search.pending = CompletableFuture.supplyAsync(() -> search.closed
                ? CompletableFuture.<ChunkResult<ChunkAccess>>completedFuture(ChunkResult.error("Search closed"))
                : search.level.getChunkSource().getChunkFuture(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true)).thenCompose(future -> future);
    }
    private static void reply(ServerPlayer player, String key, Object... args) {
        var message = Component.translatable(key, args);
        PacketDistributor.sendToPlayer(player, new ProgressReplyPayload(message, false));
        player.displayClientMessage(message, false);
    }
    private static void releaseTicket(Search search, UUID owner) {
        if (search.ticket == null) return;
        search.level.getChunkSource().removeRegionTicket(SEARCH_TICKET, search.ticket, SEARCH_DISTANCE, owner);
        search.ticket = null;
    }
    private static void stop(UUID owner) {
        var search = SEARCHES.remove(owner);
        if (search != null) { cancelPending(search); releaseTicket(search, owner); }
    }
    private static void cancelPending(Search search) {
        search.closed = true;
        if (search.pending != null) { search.pending.cancel(false); search.pending = null; }
    }
    public static void stopAll() {
        for (var owner : List.copyOf(SEARCHES.keySet())) stop(owner);
        COOLDOWNS.clear();
    }
    public static void discardAll() {
        SEARCHES.values().forEach(RuinLocator::cancelPending);
        SEARCHES.clear(); COOLDOWNS.clear();
    }
    public static Map<String, Integer> lifecycleState() {
        return Map.of("searches", SEARCHES.size(), "cooldowns", COOLDOWNS.size(),
                "tickets", (int)SEARCHES.values().stream().filter(search -> search.ticket != null).count());
    }
    public static Map<String, Object> debug(ServerPlayer player) {
        var search = SEARCHES.get(player.getUUID());
        return search == null ? Map.of("active", false) : Map.of("active", true, "checked", search.index,
                "candidates", search.candidates.size(), "failedChunks", search.failedChunks, "ticket", search.ticket != null);
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { stop(event.getEntity().getUUID()); COOLDOWNS.remove(event.getEntity().getUUID()); }
    private static final class Search {
        final ServerLevel level;
        final BlockPos origin;
        final List<ChunkPos> candidates;
        final long started;
        int index, failedChunks;
        volatile boolean closed;
        ChunkPos ticket;
        CompletableFuture<ChunkResult<ChunkAccess>> pending;
        Search(ServerLevel level, BlockPos origin, List<ChunkPos> candidates, long started) { this.level = level; this.origin = origin; this.candidates = candidates; this.started = started; }
    }
}
