package cn.wwl.orimod.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R69: Spirit Trials ghosts persisted in the world save (overworld data storage, {@code orimod_trial_ghosts}),
 * keyed by player UUID and trial. Traces are stored as 1/256-block fixed-point int arrays (at most
 * {@link ChallengeGhost#MAX_SAMPLES} points, about 7 KB per trial).
 */
public final class TrialGhosts extends SavedData {
    public static final String ID = "orimod_trial_ghosts";
    private static final double SCALE = 256;
    private final Map<String, List<Vec3>> ghosts = new HashMap<>();

    public static TrialGhosts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrialGhosts::new, TrialGhosts::load, null), ID);
    }

    public List<Vec3> ghost(String key) { return ghosts.get(key); }
    public void put(String key, List<Vec3> trace) { ghosts.put(key, List.copyOf(trace)); setDirty(); }
    public int size() { return ghosts.size(); }

    public static int[] encode(List<Vec3> trace) {
        int n = Math.min(trace.size(), ChallengeGhost.MAX_SAMPLES); var out = new int[n * 3];
        for (int i = 0; i < n; i++) {
            var v = trace.get(i);
            out[i * 3] = (int) Math.round(v.x * SCALE); out[i * 3 + 1] = (int) Math.round(v.y * SCALE); out[i * 3 + 2] = (int) Math.round(v.z * SCALE);
        }
        return out;
    }

    public static List<Vec3> decode(int[] data) {
        var out = new ArrayList<Vec3>(data.length / 3);
        for (int i = 0; i + 2 < data.length && out.size() < ChallengeGhost.MAX_SAMPLES; i += 3)
            out.add(new Vec3(data[i] / SCALE, data[i + 1] / SCALE, data[i + 2] / SCALE));
        return List.copyOf(out);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var all = new CompoundTag();
        ghosts.forEach((key, trace) -> all.putIntArray(key, encode(trace)));
        tag.put("ghosts", all); tag.putInt("version", 1);
        return tag;
    }

    public static TrialGhosts load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new TrialGhosts();
        var all = tag.getCompound("ghosts");
        for (var key : all.getAllKeys()) data.ghosts.put(key, decode(all.getIntArray(key)));
        return data;
    }
}
