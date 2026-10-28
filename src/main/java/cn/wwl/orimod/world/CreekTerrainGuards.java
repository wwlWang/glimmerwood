package cn.wwl.orimod.world;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** This piece's chosen guard cells, not a global exemption for vanilla walls.
 * A cell is recorded only after its replacement check and before the wall write.
 * Publication precedes setBlock so concurrent neighbouring scans cannot briefly
 * mistake our wall for a foreign building. Saved with the piece for later slices. */
final class CreekTerrainGuards {
    private final Set<Long> cells = ConcurrentHashMap.newKeySet();
    boolean contains(long cell) { return cells.contains(cell); }
    void plan(long cell) { cells.add(cell); }
    void restore(long[] saved) { for (long cell : saved) cells.add(cell); }
    long[] save() { return cells.stream().mapToLong(Long::longValue).sorted().toArray(); }
}
