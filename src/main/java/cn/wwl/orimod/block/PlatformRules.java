package cn.wwl.orimod.block;

/** R104: server timing and geometry, independent of world/rendering state. */
public final class PlatformRules {
    public static final int HOLD = 100, WARNING = 30, RETRY = 5, LIMIT = 256;
    public enum Source {
        LANTERN(6), FLASH(7), BURST(5), TREE(10);
        public final int radius;
        Source(int radius) { this.radius = radius; }
    }
    public static boolean within(Source source, double squaredDistance) {
        return source != null && Double.isFinite(squaredDistance) && squaredDistance >= 0
                && squaredDistance <= source.radius * source.radius;
    }
    public static long renew(long until, long now) { return Math.max(until, now + HOLD); }
    public static boolean fading(long now, long until) { return until > now && until - now <= WARNING; }
    public static boolean blink(long now, long until) { return fading(now, until) && (until - now - 1) / RETRY % 2 == 0; }
    public static boolean solidify(boolean occupied, long now, long until) { return !occupied && now < until; }
    public static int next(boolean pending, long now, long until) {
        return (int)Math.max(1, pending || fading(now, until) ? Math.min(RETRY, until-now) : until-now-WARNING);
    }
    private PlatformRules() {}
}
