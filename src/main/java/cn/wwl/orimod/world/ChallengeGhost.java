package cn.wwl.orimod.world;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * R63 Spirit Trials ghost (after WotW's race ghosts): a challenge run records the player's position every
 * {@link #STEP} ticks in ruin-local coordinates (so the trace survives any ruin rotation); the best run's trace
 * replays as a pale column of motes beside the next attempt. Traces live in server memory only.
 */
public final class ChallengeGhost {
    public static final int STEP = 2, MAX_SAMPLES = 1200 / STEP + 1;

    private ChallengeGhost() {}

    public static boolean shouldRecord(long elapsed, int samples) { return elapsed % STEP == 0 && samples < MAX_SAMPLES; }

    /** @return the ghost sample to show at this point of the new run, or null once the ghost finished. */
    public static Vec3 at(List<Vec3> trace, long elapsed) {
        if (trace == null || trace.isEmpty() || elapsed < 0) return null;
        int index = (int) (elapsed / STEP);
        return index < trace.size() ? trace.get(index) : null;
    }

    /** The new trace replaces the stored one only on a strictly better time (or when no ghost exists yet). */
    public static boolean replaces(int previousBest, boolean hasGhost, int ticks) { return !hasGhost || ticks < previousBest; }
}
