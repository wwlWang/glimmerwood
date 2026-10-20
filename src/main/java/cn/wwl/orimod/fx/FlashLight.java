package cn.wwl.orimod.fx;

import cn.wwl.orimod.config.VisualQuality;

/** R98: frame-independent Flash envelope, five ticks in and eight ticks out; shared by glow and exposure. */
public final class FlashLight {
    public static final int FADE_IN_TICKS = 5, FADE_OUT_TICKS = 8;
    public static final float BLOOM_SCALE = 2.5f, BLOOM_ALPHA = 1.6f, GROUND_RADIUS = 7, COMPANION_ABOVE = .6f,
            PETAL_SCALE = 1.5f, MAX_EXPOSURE = .08f;
    private float previous, value;
    public void tick(boolean active) {
        previous = value;
        value = Math.clamp(value + (active ? 1f / FADE_IN_TICKS : -1f / FADE_OUT_TICKS), 0, 1);
    }
    public float amount(float partial) { return previous + (value - previous) * Math.clamp(partial, 0, 1); }
    public boolean ended() { return previous == 0 && value == 0; }
    public static float exposure(float amount, VisualQuality quality, boolean decorative, int strength) {
        return decorative && quality != VisualQuality.FLUENT ? MAX_EXPOSURE * Math.clamp(amount, 0, 1) * Math.clamp(strength, 0, 100) / 100f : 0;
    }
}
