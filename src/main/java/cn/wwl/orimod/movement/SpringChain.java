package cn.wwl.orimod.movement;

/**
 * R92: secondary motion for one degree of freedom of a multi-segment appendage (an ear, feeler or the tail).
 * <p>
 * Each segment tracks its parent's absolute angle through a damped spring, and every segment also feels the same
 * inertial forcing (body acceleration, airflow, gravity), weighted per segment. Because each segment can only follow
 * the one before it, a disturbance at the root travels down the chain as a wave: the tip lags, overshoots and settles
 * last, which is what makes ears and tails read as soft instead of a rigid mesh. Values are relative joint angles in
 * radians (what each bone rotates by on top of the clip), clamped to {@link #limit}.
 * <p>
 * Integrated semi-implicitly with fixed 4 ms sub-steps, so it is stable at any frame rate.
 */
public final class SpringChain {
    private final float[] absolute, velocity, relative, stiffness, damping, gain;
    private final float limit, swayPhase;
    private float time;

    /**
     * @param stiffness per-segment spring toward the parent (1/s^2), usually softer toward the tip
     * @param damping per-segment damping (1/s)
     * @param gain per-segment weight of the forcing (rad/s^2 per unit force)
     * @param limit maximum relative angle per joint (rad)
     * @param swayPhase phase delay of the idle sway between neighbouring segments (rad)
     */
    public SpringChain(float[] stiffness, float[] damping, float[] gain, float limit, float swayPhase) {
        int n = stiffness.length;
        if (damping.length != n || gain.length != n || n == 0) throw new IllegalArgumentException("segment arrays differ");
        this.stiffness = stiffness.clone(); this.damping = damping.clone(); this.gain = gain.clone();
        this.limit = limit; this.swayPhase = swayPhase;
        absolute = new float[n]; velocity = new float[n]; relative = new float[n];
    }

    public int segments() { return absolute.length; }

    /** Relative joint angle of segment {@code i} (rad). */
    public float angle(int i) { return relative[i]; }

    /** Absolute angle of the tip against the body (rad). */
    public float tip() { return absolute[absolute.length - 1]; }

    public float[] angles() { return relative.clone(); }

    public void reset() {
        java.util.Arrays.fill(absolute, 0); java.util.Arrays.fill(velocity, 0); java.util.Arrays.fill(relative, 0);
    }

    /** Add angular velocity to every segment, more toward the tip (landing flop, jump flick). */
    public void kick(float impulse) {
        for (int i = 0; i < absolute.length; i++) velocity[i] += impulse * (1 + i * .35f);
    }

    /**
     * Advance by {@code dt} seconds under a forcing; {@code sway} is an idle travelling wave amplitude (rad/s^2) at
     * angular frequency {@code swayRate} (rad/s).
     */
    public void step(float force, float sway, float swayRate, float dt) {
        if (!(dt > 0)) return;
        int n = Math.max(1, (int) Math.ceil(Math.min(dt, .1f) / .004f));
        float h = Math.min(dt, .1f) / n;
        for (int s = 0; s < n; s++) {
            time += h;
            float parent = 0, parentVelocity = 0;
            for (int i = 0; i < absolute.length; i++) {
                float wave = sway == 0 ? 0 : sway * (float) Math.sin(time * swayRate - i * swayPhase);
                float accel = stiffness[i] * (parent - absolute[i]) - damping[i] * (velocity[i] - parentVelocity)
                        + gain[i] * (force + wave);
                velocity[i] += accel * h;
                absolute[i] += velocity[i] * h;
                float rel = absolute[i] - parent;
                if (rel > limit || rel < -limit) {
                    rel = Math.clamp(rel, -limit, limit);
                    absolute[i] = parent + rel;
                    velocity[i] = parentVelocity;
                }
                parent = absolute[i]; parentVelocity = velocity[i];
            }
        }
        float parent = 0;
        for (int i = 0; i < absolute.length; i++) { relative[i] = absolute[i] - parent; parent = absolute[i]; }
        if (time > 3600) time -= 3600;
    }
}
