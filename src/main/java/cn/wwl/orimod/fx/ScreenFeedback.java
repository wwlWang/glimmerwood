package cn.wwl.orimod.fx;

/**
 * Camera shake and screen flash timeline. Pure and deterministic: callers feed ticks and the two user
 * percentages (0 disables). Skills never pass their own strength past these settings.
 */
public final class ScreenFeedback {
    /** Peak roll in degrees at 100% shake for a full-strength trigger. */
    public static final double MAX_SHAKE_DEGREES = 2.5;
    /** Peak overlay alpha at 100% flash. */
    public static final double MAX_FLASH_ALPHA = 0.35;
    public static final int SHAKE_TICKS = 6, FLASH_TICKS = 4;

    public record Angles(double pitch, double yaw, double roll) {
        public static final Angles ZERO = new Angles(0, 0, 0);
        public boolean isZero() { return pitch == 0 && yaw == 0 && roll == 0; }
    }

    private double shakeAmplitude, flashAlpha;
    private int shakeAge = SHAKE_TICKS, flashAge = FLASH_TICKS;
    private long seed;

    public static double fraction(int percent) { return Math.clamp(percent, 0, 100) / 100.0; }

    /** Taking damage from any source: strongest shake plus the flash. */
    public void hurt(int shakePercent, int flashPercent) { shake(1.0, shakePercent); flash(1.0, flashPercent); }

    /** Own stomp landing: lighter shake, no flash. */
    public void stomp(int shakePercent) { shake(0.6, shakePercent); }

    /** Own blade connecting: a small kick, stronger on the finisher, so hits read as weight rather than a click. */
    public void bladeHit(int shakePercent, boolean finisher) { shake(finisher ? 0.42 : 0.2, shakePercent); }

    private void shake(double strength, int percent) {
        double amplitude = MAX_SHAKE_DEGREES * strength * fraction(percent);
        if (amplitude <= 0) return;
        // A new trigger never weakens one already in progress.
        if (amplitude >= currentShake(0)) { shakeAmplitude = amplitude; shakeAge = 0; seed++; }
    }

    private void flash(double strength, int percent) {
        double alpha = MAX_FLASH_ALPHA * strength * fraction(percent);
        if (alpha <= 0) return;
        if (alpha >= flashAlpha(0)) { flashAlpha = alpha; flashAge = 0; }
    }

    public void tick() {
        if (shakeAge < SHAKE_TICKS) shakeAge++;
        if (flashAge < FLASH_TICKS) flashAge++;
    }

    /** Settings changed to zero mid-effect, menu opened, form changed, death or disconnect. */
    public void clear() { shakeAge = SHAKE_TICKS; flashAge = FLASH_TICKS; shakeAmplitude = flashAlpha = 0; }

    /** Peak strengths of the latest accepted triggers, for diagnostics. */
    public double lastShakeAmplitude() { return shakeAmplitude; }
    public double lastFlashAlpha() { return flashAlpha; }

    public boolean active() { return shakeAge < SHAKE_TICKS || flashAge < FLASH_TICKS; }

    private double currentShake(double partial) {
        double t = (shakeAge + partial) / SHAKE_TICKS;
        return t >= 1 ? 0 : shakeAmplitude * (1 - t) * (1 - t);
    }

    public Angles angles(double partial) {
        double amplitude = currentShake(Math.clamp(partial, 0, 1));
        if (amplitude <= 0) return Angles.ZERO;
        double phase = (shakeAge + partial) * 2.4 + seed * 1.7;
        return new Angles(amplitude * .5 * Math.sin(phase * 1.3), amplitude * .35 * Math.sin(phase * .9 + 1), amplitude * Math.sin(phase));
    }

    public double flashAlpha(double partial) {
        double t = (flashAge + Math.clamp(partial, 0, 1)) / FLASH_TICKS;
        return t >= 1 ? 0 : flashAlpha * (1 - t);
    }
}
