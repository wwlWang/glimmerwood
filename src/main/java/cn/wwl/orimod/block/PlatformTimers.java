package cn.wwl.orimod.block;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;

/** One small per-dimension ledger, not a block entity or a per-tick traversal.
 * Absolute game-time deadlines survive chunk unload/server restart; vanilla scheduled ticks drive work. */
public final class PlatformTimers extends SavedData {
    public record Lease(long pulseUntil, long until) {}
    private final Map<Long, Lease> leases = new HashMap<>();
    private final Map<Long, java.util.Set<Long>> byChunk = new HashMap<>();
    private final java.util.NavigableMap<Long, java.util.Set<Long>> byExpiry = new java.util.TreeMap<>();
    private java.util.function.LongSupplier clock = () -> Long.MIN_VALUE;
    public static PlatformTimers get(ServerLevel level) {
        var data=level.getDataStorage().computeIfAbsent(new Factory<>(PlatformTimers::new, PlatformTimers::load, null), "orimod_platform_timers");
        data.clock=level::getGameTime;data.pruneExpired(level.getGameTime());return data;
    }
    public Lease get(long pos) { return leases.get(pos); }
    public int size() { return leases.size(); }
    private static long chunk(long pos) { var p=net.minecraft.core.BlockPos.of(pos);return net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4); }
    public java.util.Set<Long> positions(long chunk) { return java.util.Set.copyOf(byChunk.getOrDefault(chunk, java.util.Set.of())); }
    public void put(long pos, Lease lease) {
        var old=leases.put(pos,lease);
        if (!lease.equals(old)) {
            if(old!=null)unindexExpiry(pos,old.until());
            byExpiry.computeIfAbsent(lease.until(),k->new java.util.HashSet<>()).add(pos);
            byChunk.computeIfAbsent(chunk(pos),k->new java.util.HashSet<>()).add(pos); setDirty();
        }
    }
    public void remove(long pos) {
        var old=leases.remove(pos);if(old==null)return;unindexExpiry(pos,old.until());
        var points=byChunk.get(chunk(pos));if(points!=null) { points.remove(pos);if(points.isEmpty())byChunk.remove(chunk(pos)); }
        setDirty();
    }
    private void unindexExpiry(long pos,long until) {
        var points=byExpiry.get(until);if(points!=null){points.remove(pos);if(points.isEmpty())byExpiry.remove(until);}
    }
    /** Also retire expired leases in unloaded chunks, without inspecting/loading those chunks. */
    public void pruneExpired(long now) {
        while(!byExpiry.isEmpty() && byExpiry.firstKey()<=now)
            for(long pos:java.util.Set.copyOf(byExpiry.firstEntry().getValue()))remove(pos);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        pruneExpired(clock.getAsLong());
        var entries = new ListTag();
        leases.forEach((pos, lease) -> {
            var e = new CompoundTag(); e.putLong("pos",pos); e.putLong("pulse",lease.pulseUntil()); e.putLong("until",lease.until()); entries.add(e);
        });
        tag.put("leases",entries); return tag;
    }
    public static PlatformTimers load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PlatformTimers();
        for (var value : tag.getList("leases",Tag.TAG_COMPOUND)) {
            var e = (CompoundTag)value; data.put(e.getLong("pos"), new Lease(e.getLong("pulse"),e.getLong("until")));
        }
        data.setDirty(false);
        return data;
    }
}
