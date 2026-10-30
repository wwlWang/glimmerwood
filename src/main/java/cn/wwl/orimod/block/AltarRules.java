package cn.wwl.orimod.block;

/** Cooldown uses monotonic world game time, so sleeping and /time do not bypass it. */
public final class AltarRules {
    public static final long COOLDOWN_TICKS = 24_000;
    private AltarRules() {}
    public static long remaining(long now, long readyAt) { return Math.max(0, readyAt - now); }
    public static long seconds(long ticks) { return (Math.max(0, ticks) + 19) / 20; }
}
