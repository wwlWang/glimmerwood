package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Independent measurements of occupied leaf columns; shared by the shape regression and review report. */
final class SpiritTreeShapeMetrics {
    private SpiritTreeShapeMetrics() {}
    record Underside(SortedMap<Integer, Integer> histogram, int columns, int minY, int maxY, int modeY, int modeCount) {
        double modeFraction() { return (double) modeCount / columns; }
    }

    static Underside underside(SpiritTreeShape.Plan plan) {
        return underside(plan, 0);
    }

    static Underside underside(SpiritTreeShape.Plan plan, int minimumRadius) {
        var bottoms = new HashMap<BlockPos, Integer>();
        plan.blocks().forEach((p, part) -> {
            if (part == SpiritTreeShape.Part.LEAVES && p.getX() * p.getX() + p.getZ() * p.getZ() >= minimumRadius * minimumRadius)
                bottoms.merge(new BlockPos(p.getX(), 0, p.getZ()), p.getY(), Math::min);
        });
        var histogram = new TreeMap<Integer, Integer>();
        bottoms.values().forEach(y -> histogram.merge(y, 1, Integer::sum));
        var mode = histogram.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        return new Underside(Collections.unmodifiableSortedMap(histogram), bottoms.size(),
                histogram.firstKey(), histogram.lastKey(), mode.getKey(), mode.getValue());
    }
}
