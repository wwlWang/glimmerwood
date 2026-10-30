package cn.wwl.orimod.progression;

/** R117 exploration shards. Values and state transitions are independent of the game runtime. */
public final class ExplorationShardRules {
    public static final double MAGNET_RADIUS = 6, MAGNET_SPEED = .35;
    public static final int MAGNET_INTERVAL = 2, PROSPECTOR_RADIUS = 4, PROSPECTOR_INTERVAL = 100,
            PROSPECTOR_CHANCE = 6, FORAGER_CHANCE = 10, FIRE_BURN_RATE = 4;
    public static final float LAVA_MULTIPLIER = .5f, NIGHT_STRENGTH = .55f, NIGHT_FADE_STEP = .025f;
    public static final int DARK_ON = 5, DARK_OFF = 9;
    private ExplorationShardRules() {}

    /** Halve actual air loss, including respiration's skipped ticks, without rounding every tick to zero. */
    public static final class Breath {
        private int carry;
        public int consume(int original, boolean active) {
            if (!active) { carry = 0; return original; }
            int amount = Math.max(0, original);
            int result = amount / 2 + (amount % 2 + carry) / 2;
            carry = (amount % 2 + carry) % 2;
            return result;
        }
    }
    public static boolean ready(long now, long last) {
        return last == Long.MIN_VALUE || now < last || now - last >= PROSPECTOR_INTERVAL;
    }
    public static boolean dark(boolean previous, int light) { return light <= (previous ? DARK_OFF - 1 : DARK_ON); }
    public static float fade(float current, boolean dark) {
        return dark ? Math.min(NIGHT_STRENGTH, current + NIGHT_FADE_STEP) : Math.max(0, current - NIGHT_FADE_STEP);
    }
    public static int fireTicks(int remaining) { return Math.max(0, remaining - FIRE_BURN_RATE + 1); }
    public static float lavaDamage(float damage) { return damage * LAVA_MULTIPLIER; }
}
