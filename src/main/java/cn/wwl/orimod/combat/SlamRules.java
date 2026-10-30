package cn.wwl.orimod.combat;

/** Hammer and Stomp share a bounded fall curve and height-based impact. */
public final class SlamRules {
    public static final double START_SPEED = -1.2, ACCELERATION = .22, TERMINAL_SPEED = -3.3;
    public static final double FULL_IMPACT_HEIGHT = 24, MAX_STRENGTH = 1.75;
    private SlamRules() {}
    public static double initialSpeed(double current) { return Math.clamp(current, TERMINAL_SPEED, START_SPEED); }
    public static double nextSpeed(double current) { return Math.max(TERMINAL_SPEED, Math.min(START_SPEED, current) - ACCELERATION); }
    public static float strength(double fallen) {
        if (!Double.isFinite(fallen)) return 1;
        return (float)(1 + (MAX_STRENGTH - 1) * Math.clamp(fallen / FULL_IMPACT_HEIGHT, 0, 1));
    }
    public static float radiusScale(float strength) { return 1 + (Math.clamp(strength, 1, (float)MAX_STRENGTH) - 1) * .5f; }
}
