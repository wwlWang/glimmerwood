package cn.wwl.orimod.world;

import net.minecraft.world.Difficulty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure tide decisions, shared by the server and regression tests. Distances are squared. */
public final class GloomTideRules {
    public static final int LANTERN_RADIUS = 10, FLASH_RADIUS = 32, TREE_RADIUS = 32, NEARBY_RADIUS = 48;
    public static final int MIN_SPAWN = 16, MAX_SPAWN = 28, DAWN_KILLS = 5, DAWN_LIGHT = 50;
    public static boolean night(long time) { long t = Math.floorMod(time, 24000); return t >= 13000 && t < 23000; }
    public static long day(long time) { return Math.floorDiv(time, 24000); }
    public static boolean allowed(long time, Difficulty difficulty, boolean spawning, boolean enabled) {
        return night(time) && difficulty != Difficulty.PEACEFUL && spawning && enabled;
    }
    public static boolean isTideNight(long time, int moon, Difficulty difficulty, boolean spawning, boolean enabled) {
        return moon == 4 && allowed(time, difficulty, spawning, enabled);
    }
    public static boolean spawnDistance(double horizontalSquared) {
        return horizontalSquared >= MIN_SPAWN * MIN_SPAWN && horizontalSquared <= MAX_SPAWN * MAX_SPAWN;
    }
    public static boolean roomForMob(int nearby, int max) { return nearby >= 0 && nearby < max; }
    public static boolean within(double squared, int radius) { return squared >= 0 && squared <= radius * radius; }
    public static boolean defended(double lanternSquared, double flashSquared, double treeSquared) {
        return within(lanternSquared, LANTERN_RADIUS) || within(flashSquared, FLASH_RADIUS) || within(treeSquared, TREE_RADIUS);
    }

    /** Unsaved in ENERGY_STATE. A backward jump to an earlier day starts a new timeline/night.
     * Re-entering the same day preserves its settlement guard, even after /tide stop/start. */
    public static final class NightLedger {
        public long day = Long.MIN_VALUE, settledDay = Long.MIN_VALUE, nextWaveAt;
        public int kills;
        public boolean participating;
        public void begin(long newDay, long now) {
            if (newDay < day) settledDay = Long.MIN_VALUE;
            if (newDay != day) { day = newDay; kills = 0; }
            participating = true;
            nextWaveAt = now;
        }
        public int finish() {
            participating = false;
            if (settledDay == day) return 0;
            settledDay = day;
            return kills >= DAWN_KILLS ? DAWN_LIGHT : 0;
        }
    }

    /** A chunk belongs to at most one player's wave in a game tick; a wave may contain several mobs. */
    public static final class WaveClaims {
        private long tick = Long.MIN_VALUE;
        private final Map<Long, UUID> owners = new HashMap<>();
        public boolean available(long now, long chunk, UUID player) {
            if (tick != now) { owners.clear(); tick = now; }
            return !owners.containsKey(chunk) || owners.get(chunk).equals(player);
        }
        public void claim(long now, long chunk, UUID player) {
            if (!available(now, chunk, player)) throw new IllegalStateException("Chunk already has a tide wave");
            owners.put(chunk, player);
        }
    }
    public enum WaveMob { SPITTER, BEETLE, HAWK }
    public static java.util.List<WaveMob> wave(int extraSpitter,float beetleRoll,float hawkRoll){
        var mobs=new java.util.ArrayList<WaveMob>();mobs.add(WaveMob.SPITTER);
        if(extraSpitter==1)mobs.add(WaveMob.SPITTER);
        if(beetleRoll<.35f)mobs.add(WaveMob.BEETLE);
        if(hawkRoll<.3f)mobs.add(WaveMob.HAWK);
        return java.util.List.copyOf(mobs);
    }
    private GloomTideRules() {}
}
