package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;

/**
 * R73 game-feel tuning, all in one place (see docs/FEEL_AUDIT.md). Spirit-form ground and air motion is driven on the
 * client (Minecraft player movement is client-authoritative; the server keeps validating actions), replacing vanilla's
 * sluggish friction model with an explicit approach curve, plus Ori-style vertical shaping: lighter rise while jump is
 * held, a release cut (variable jump height), apex hang and heavier falls. Units: blocks and ticks (20 TPS).
 * scripts/feel_curves.py reads these constants to plot the before/after curves.
 */
public final class FeelTuning {
    // ---- horizontal ---------------------------------------------------------------------------------------
    public static final double TOP_SPEED = .32;          // 6.4 m/s (vanilla walk .216, sprint .286 terminal)
    public static final double GROUND_ACCEL = .11;       // 0 -> top in 3 ticks
    public static final double GROUND_DECEL = .18;       // top -> 0 in 2 ticks
    public static final double AIR_ACCEL = .06;          // vanilla air control .02/tick
    public static final double AIR_DRAG = .012;          // no input in the air: keep momentum
    public static final double OVERSPEED_DECAY_GROUND = .035, OVERSPEED_DECAY_AIR = .008;   // dash/bash/launch carry
    public static final double CROUCH_SCALE = .35, USING_ITEM_SCALE = .45;
    public static final int WALL_JUMP_CONTROL_TICKS = 7; // reduced air steering right after a wall jump
    public static final double WALL_JUMP_CONTROL_SCALE = .25;
    // ---- vertical ----------------------------------------------------------------------------------------
    public static final double GRAVITY = .08;            // vanilla
    public static final double RISE_GRAVITY_SCALE = .72; // jump held while rising
    public static final double APEX_SPEED = .1, APEX_GRAVITY_SCALE = .45;
    public static final double FALL_GRAVITY_SCALE = 1.35, MAX_FALL = -1.4;
    public static final double JUMP_CUT = .45, JUMP_CUT_MIN = .12;
    // ---- dash ---------------------------------------------------------------------------------------------
    /** Dash speed multiplier by tick (DASH_TICKS long): a burst, then ease out instead of a hard stop. */
    public static final double[] DASH_PROFILE = {1.0, 1.0, .92, .78, .62};
    public static final double DASH_EXIT_SPEED = .46;    // carried into the run, decays to TOP_SPEED

    // ---- animation and pose (client/render/SpiritAnimation, SpiritPlayerVisual) ------------------------------
    public static final float ANIM_CROSSFADE = .2f, ANIM_CROSSFADE_FAST = .09f;   // seconds (4 / ~2 ticks)
    public static final float FACING_RATE = 16;           // 1/s: model turns toward its velocity
    public static final float FACING_MIN_SPEED = 1.2f;    // m/s
    public static final float LEAN_SPEED = .1f, LEAN_ACCEL = .018f, LEAN_MAX = .3f;     // rad, rad per m/s^2
    public static final float ROLL_PER_TURN = .0011f, ROLL_MAX = .3f;                  // rad per (deg/s * m/s / 6.4)
    public static final float SQUASH_JUMP = 2.0f, SQUASH_LAND = 3.6f;                  // spring velocity impulses
    public static final float BREATH_PERIOD = 3.2f, BREATH_AMPLITUDE = .03f;
    /** Damped springs: stiffness (1/s^2) and damping (1/s); damping below 2*sqrt(stiffness) overshoots. */
    public static final float POSE_STIFFNESS = 180, POSE_DAMPING = 20;
    public static final float SQUASH_STIFFNESS = 260, SQUASH_DAMPING = 14;
    public static final float EAR_STIFFNESS = 120, EAR_DAMPING = 9;
    public static final float EAR_TURN = .0016f, EAR_FALL = .55f, EAR_MAX = .6f;
    // ---- camera and FX -----------------------------------------------------------------------------------
    public static final float FOV_KICK_DASH = .1f, FOV_KICK_SPEED = .06f, FOV_RATE = 10;
    public static final float TRAIL_MIN_SPEED = 1.0f;     // m/s
    public static final int FOOTSTEP_SPACING_TICKS = 5;

    /** A damped spring value: {@code x'' = k (target - x) - c x'} integrated semi-implicitly. */
    public static final class Spring {
        public float value, velocity;
        private final float stiffness, damping;
        public Spring(float value, float stiffness, float damping) { this.value = value; this.stiffness = stiffness; this.damping = damping; }
        public float step(float target, float dt) {
            // Sub-step so low frame rates stay stable.
            int n = Math.max(1, (int) Math.ceil(dt / .008f)); float h = dt / n;
            for (int i = 0; i < n; i++) { velocity += (stiffness * (target - value) - damping * velocity) * h; value += velocity * h; }
            return value;
        }
        public void kick(float impulse) { velocity += impulse; }
        public void reset(float v) { value = v; velocity = 0; }
    }

    /** Frame-rate independent exponential approach for angles in degrees. */
    public static float approachAngle(float current, float target, float rate, float dt) {
        float diff = net.minecraft.util.Mth.wrapDegrees(target - current);
        return current + diff * (1 - (float) Math.exp(-rate * dt));
    }

    private FeelTuning() {}

    /** Move {@code current} toward {@code target} by at most accel (speeding up) or decel (braking/reversing). */
    public static Vec3 approach(Vec3 current, Vec3 target, double accel, double decel) {
        Vec3 diff = target.subtract(current);
        double len = diff.length();
        if (len < 1e-9) return target;
        boolean braking = target.lengthSqr() < current.lengthSqr() - 1e-9 || target.dot(current) < 0;
        double step = braking ? decel : accel;
        return len <= step ? target : current.add(diff.scale(step / len));
    }

    /** One tick of horizontal feel. {@code wish} is the unit input direction (zero for none). */
    public static Vec3 horizontal(Vec3 velocity, Vec3 wish, boolean grounded, double scale, double control) {
        Vec3 v = new Vec3(velocity.x, 0, velocity.z);
        double speed = v.length(), top = TOP_SPEED * scale;
        boolean input = wish.lengthSqr() > 1e-4;
        if (speed > top + 1e-6 && (!input || wish.dot(v) > 0)) {
            // Momentum carry: above the run speed we bleed slowly and keep steering along the input.
            double decayed = Math.max(top, speed - (grounded ? OVERSPEED_DECAY_GROUND : OVERSPEED_DECAY_AIR));
            Vec3 dir = input ? approach(v.normalize(), wish, (grounded ? .35 : .12) * control, (grounded ? .35 : .12) * control).normalize() : v.normalize();
            return dir.scale(decayed);
        }
        if (grounded) return approach(v, wish.scale(top), GROUND_ACCEL * control, GROUND_DECEL * control);
        if (!input) return speed <= AIR_DRAG ? Vec3.ZERO : v.scale((speed - AIR_DRAG) / speed);
        return approach(v, wish.scale(top), AIR_ACCEL * control, AIR_ACCEL * control);
    }

    /**
     * Vertical shaping applied before vanilla travel (which then subtracts {@link #GRAVITY} and applies 0.98 drag).
     * @return the adjusted vertical velocity.
     */
    public static double vertical(double vy, boolean jumpHeld, boolean released) {
        if (released && vy > JUMP_CUT_MIN) return vy * JUMP_CUT;
        double scale;
        if (Math.abs(vy) < APEX_SPEED && jumpHeld) scale = APEX_GRAVITY_SCALE;
        else if (vy > 0) scale = jumpHeld ? RISE_GRAVITY_SCALE : 1;
        else scale = FALL_GRAVITY_SCALE;
        // Vanilla subtracts GRAVITY next; pre-compensate so the effective gravity is GRAVITY * scale.
        double adjusted = vy + GRAVITY * (1 - scale);
        // Terminal fall speed; faster external motion (Stomp etc.) is left alone.
        return vy < MAX_FALL - .05 ? adjusted : Math.max(adjusted, MAX_FALL / .98 + GRAVITY);
    }

    /** Dash speed for a dash with {@code ticksLeft} of DASH_TICKS remaining. */
    public static double dashScale(int ticksLeft) {
        int i = MovementRules.DASH_TICKS - Math.max(1, Math.min(MovementRules.DASH_TICKS, ticksLeft));
        return DASH_PROFILE[Math.min(DASH_PROFILE.length - 1, i)];
    }

    /** R75: FOV kick target (fraction) for dash / run speed in m/s; vanilla FOV effect scale is applied by the caller. */
    public static float fovKickTarget(boolean dashing, double speedMps) {
        if (dashing) return FOV_KICK_DASH;
        double t = (speedMps - 4.5) / 2.5;
        return (float) (FOV_KICK_SPEED * Math.max(0, Math.min(1, t)));
    }

    /** R75: trail motes per tick for a given speed; low quality halves it, no advanced particles keeps one every other tick. */
    public static int trailMotes(double speedMps, double particleScale, boolean advanced, long tick) {
        if (speedMps < TRAIL_MIN_SPEED) return 0;
        if (!advanced) return tick % 2 == 0 ? 1 : 0;
        int n = speedMps > 8 ? 3 : speedMps > 4 ? 2 : 1;
        return Math.max(1, (int) Math.round(n * particleScale));
    }
}
