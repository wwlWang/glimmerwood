package cn.wwl.orimod.fx;

/** Pure interpolation shared by fog/sky/star rendering. A full fade takes exactly 100 ticks. */
public final class GloomTideVisuals {
    public static final int COLOR = 0x2A1638, FADE_TICKS = 100;
    public static final float STRENGTH = .35f;
    public static float approach(float current, float target) {
        float step = STRENGTH / FADE_TICKS;
        return Math.abs(target - current) <= step ? target : current + Math.copySign(step, target - current);
    }
    public static float mix(float original, int channel, float intensity) { return original + (channel / 255f - original) * intensity; }
    /** Last server state survives replacement of ClientLevel, including packets arriving before the switch.
     * Bind clears interpolation only; disconnect clears the server state too. */
    public static final class Fade {
        private float received, previous, current, target;
        private boolean overworld;
        public void receive(boolean active, float intensity) {
            received = active ? intensity : 0;
            target = overworld ? received : 0;
        }
        public void bind(boolean inOverworld) {
            overworld = inOverworld;
            previous = current = 0;
            target = overworld ? received : 0;
        }
        public void tick() { previous = current; current = approach(current, target); }
        public float value(float partial) { return previous + (current - previous) * Math.clamp(partial, 0, 1); }
        public float target() { return target; }
        public void disconnect() { received = previous = current = target = 0; overworld = false; }
    }
    private GloomTideVisuals() {}
}
