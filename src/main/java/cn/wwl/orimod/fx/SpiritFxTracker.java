package cn.wwl.orimod.fx;

import java.util.*;
import java.util.function.IntPredicate;

/**
 * Client-side bookkeeping for persistent spirit effects, free of game classes so it can be unit tested.
 * An effect lives from START until STOP/STOP_ALL, until its entity is gone, until the world is left,
 * or until the server's heartbeat has been missing for {@link #EXPIRY_TICKS}.
 */
public final class SpiritFxTracker {
    /** The server refreshes live effects this often. */
    public static final int HEARTBEAT_TICKS = 20;
    /** Three missed heartbeats end an effect, so a lost STOP can never leave a loop running. */
    public static final int EXPIRY_TICKS = HEARTBEAT_TICKS * 3;

    public record Key(int entity, SpiritFxKind kind) {}

    private final Map<Key, Long> active = new LinkedHashMap<>();

    /** @return true when the effect was not already running. */
    public boolean start(int entity, SpiritFxKind kind, long now) {
        if (!kind.persistent()) return false;
        return active.put(new Key(entity, kind), now) == null;
    }

    public boolean stop(int entity, SpiritFxKind kind) { return active.remove(new Key(entity, kind)) != null; }

    public List<Key> stopAll(int entity) {
        var removed = new ArrayList<Key>();
        for (var it = active.keySet().iterator(); it.hasNext(); ) {
            var key = it.next();
            if (key.entity() == entity) { removed.add(key); it.remove(); }
        }
        return removed;
    }

    /** Drops expired effects and effects whose entity is no longer present. */
    public List<Key> tick(long now, IntPredicate entityPresent) {
        var removed = new ArrayList<Key>();
        for (var it = active.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now - entry.getValue() > EXPIRY_TICKS || !entityPresent.test(entry.getKey().entity())) {
                removed.add(entry.getKey()); it.remove();
            }
        }
        return removed;
    }

    public List<Key> clear() { var removed = new ArrayList<>(active.keySet()); active.clear(); return removed; }
    public Set<Key> active() { return Collections.unmodifiableSet(active.keySet()); }
    public boolean isActive(int entity, SpiritFxKind kind) { return active.containsKey(new Key(entity, kind)); }
}
