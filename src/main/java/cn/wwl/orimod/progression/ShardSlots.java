package cn.wwl.orimod.progression;

import java.util.ArrayList;
import java.util.List;

/** Four ordered slots backed by the existing equipment mask. Old saves fill slots in catalogue order. */
public final class ShardSlots {
    public static final int MAX = 4;
    private ShardSlots() {}
    public static List<Integer> normalize(List<Integer> saved, int equipped) {
        var slots = new ArrayList<Integer>();
        int used = 0;
        for (int i = 0; i < MAX; i++) {
            int bit = i < saved.size() ? saved.get(i) : 0;
            if (Integer.bitCount(bit) != 1 || (bit & equipped & ~used) == 0) bit = 0;
            slots.add(bit); used |= bit;
        }
        for (int bit = 1; bit <= Shard.MASK && slots.contains(0); bit <<= 1)
            if ((bit & equipped & ~used) != 0) { slots.set(slots.indexOf(0), bit); used |= bit; }
        return List.copyOf(slots);
    }
    public static List<Integer> put(List<Integer> slots, int slot, int bit) {
        if (slot < 0 || slot >= MAX || Integer.bitCount(bit) != 1 || (bit & Shard.MASK) == 0) return slots;
        var result = new ArrayList<>(slots);
        int from = result.indexOf(bit), replaced = result.get(slot);
        if (from >= 0) result.set(from, replaced);
        result.set(slot, bit);
        return List.copyOf(result);
    }
    public static int mask(List<Integer> slots) { return slots.stream().reduce(0, (a, b) -> a | b); }
}
