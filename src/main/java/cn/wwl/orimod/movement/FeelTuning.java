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
    public static final double OVERSPEED_DAMP_GROUND = .15, OVERSPEED_DAMP_AIR = .02;      // R77: plus this fraction of the excess per tick
    public static final double CROUCH_SCALE = .35, USING_ITEM_SCALE = .45;
    public static final int WALL_JUMP_CONTROL_TICKS = 7; // reduced air steering right after a wall jump
    public static final double WALL_JUMP_CONTROL_SCALE = .25;
    // ---- vertical ----------------------------------------------------------------------------------------
    public static final double GRAVITY = .08;            // vanilla
    // R88: playtest said the fall had "no acceleration": the R85 curve itself accelerated, but holding jump turned
    // every fall into a constant-speed glide (fixed in MovementRules.glideArmed). The fall now also ramps its gravity
    // from 1.25x at the apex to 1.85x by 0.6 b/t down, so the speed-up is visible instead of an instant snap.
    public static final double RISE_GRAVITY_SCALE = .8;  // jump held while rising (R85 .9, R73 .72)
    public static final double APEX_SPEED = .07, APEX_GRAVITY_SCALE = .6;  // short hang at the top (R85 .08/.8)
    public static final double FALL_GRAVITY_START = 1.25, FALL_GRAVITY_SCALE = 1.85, FALL_RAMP_SPEED = .6;
    public static final double MAX_FALL = -1.4;          // 28 m/s terminal (R85 -1.55)
    public static final double JUMP_CUT = .4, JUMP_CUT_MIN = .1;
    // ---- dash ---------------------------------------------------------------------------------------------
    /** Dash speed multiplier by tick (DASH_TICKS long): a burst, then ease out instead of a hard stop. */
    public static final double[] DASH_PROFILE = {1.0, 1.0, .92, .78, .62};
    public static final double DASH_EXIT_SPEED = .46;    // carried into the run, decays to TOP_SPEED

    // ---- animation and pose (client/render/SpiritAnimation, SpiritPlayerVisual) ------------------------------
    public static final float ANIM_CROSSFADE = .2f, ANIM_CROSSFADE_FAST = .09f;   // seconds (4 / ~2 ticks)
    public static final float FACING_RATE = 16;           // 1/s: model turns toward its velocity
    public static final float FACING_MIN_SPEED = 1.2f;    // m/s
    // R88: Ori runs low and leaned; R74's ~6 degrees at top speed read as an upright vanilla jog.
    public static final float LEAN_SPEED = .2f, LEAN_ACCEL = .018f, LEAN_MAX = .38f;    // rad, rad per m/s^2
    /** R88 air posture: lean with horizontal speed, nose up while rising and diving forward while falling (rad per b/t). */
    public static final float AIR_LEAN_SPEED = .12f, AIR_LEAN_VERTICAL = .35f;
    /** R88: vertical bounce of the run (squash target swing, two per stride) and stretch with vertical speed in the air. */
    public static final float GAIT_BOUNCE = .035f, AIR_STRETCH = .12f, AIR_STRETCH_MAX = .08f;
    /** R88: continuous rise/apex/fall blend by vertical speed (b/t) instead of hard-switched clips. */
    public static final float AIR_RISE_FROM = .02f, AIR_RISE_TO = .22f, AIR_FALL_FROM = .02f, AIR_FALL_TO = .3f;
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
            double decayed = Math.max(top, speed - (grounded ? OVERSPEED_DECAY_GROUND : OVERSPEED_DECAY_AIR)
                    - (speed - top) * (grounded ? OVERSPEED_DAMP_GROUND : OVERSPEED_DAMP_AIR));
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
        else scale = fallGravityScale(vy);
        // Vanilla subtracts GRAVITY next; pre-compensate so the effective gravity is GRAVITY * scale.
        double adjusted = vy + GRAVITY * (1 - scale);
        // Terminal fall speed; faster external motion (Stomp etc.) is left alone.
        return vy < MAX_FALL - .05 ? adjusted : Math.max(adjusted, MAX_FALL / .98 + GRAVITY);
    }

    /** R88: falling gravity grows with fall speed: {@link #FALL_GRAVITY_START} at the apex to {@link #FALL_GRAVITY_SCALE}. */
    public static double fallGravityScale(double vy) {
        double k = Math.max(0, Math.min(1, -vy / FALL_RAMP_SPEED));
        return FALL_GRAVITY_START + (FALL_GRAVITY_SCALE - FALL_GRAVITY_START) * k;
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
        // R86: denser trail so the light reads continuous (was 1/2/3).
        int n = speedMps > 8 ? 5 : speedMps > 4 ? 4 : 2;
        return Math.max(1, (int) Math.round(n * particleScale));
    }

    // ---- R80 gait blend --------------------------------------------------------------------------------------------
    public static final float GAIT_RUN_FROM = 1.4f, GAIT_RUN_TO = 3.4f, GAIT_SPRINT_FROM = 6.6f, GAIT_SPRINT_TO = 9f;   // m/s
    public static final float STRIDE_WALK = .8f, STRIDE_RUN = 2.0f, STRIDE_SPRINT = 2.6f, SPRINT_LEAN = .06f;           // metres per cycle, rad

    public static float smoothstep(float a, float b, float x) { float t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    /** Weights {walk, run, sprint} summing to 1; sprint is the run clip played with a longer stride and extra lean. */
    public static float[] gaitWeights(float speed) {
        float run = smoothstep(GAIT_RUN_FROM, GAIT_RUN_TO, speed), sprint = run * smoothstep(GAIT_SPRINT_FROM, GAIT_SPRINT_TO, speed);
        return new float[] {1 - run, run - sprint, sprint};
    }

    /** R88: weights {rise, apex, fall} for a vertical speed in blocks per tick; they sum to 1 and change smoothly. */
    public static float[] airWeights(float vy) {
        float rise = smoothstep(AIR_RISE_FROM, AIR_RISE_TO, vy), fall = smoothstep(AIR_FALL_FROM, AIR_FALL_TO, -vy);
        return new float[] {rise, 1 - rise - fall, fall};
    }

    /** R88: body pitch target in the air (positive = forward): speed lean plus nose-up rise / forward dive. */
    public static float airLean(float speedMps, float vy) {
        return Math.max(-LEAN_MAX, Math.min(LEAN_MAX, speedMps / 6.4f * AIR_LEAN_SPEED - vy * AIR_LEAN_VERTICAL));
    }

    /** Metres per locomotion cycle for the blended gait, so feet keep pace at every speed. */
    public static float stride(float speed) {
        float[] w = gaitWeights(speed);
        return w[0] * STRIDE_WALK + w[1] * STRIDE_RUN + w[2] * STRIDE_SPRINT;
    }

    public static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    // ---- R79 third-person camera follow ------------------------------------------------------------------------------
    public static final double CAMERA_LAG_RATE = 11, CAMERA_LAG_MAX = .55;              // 1/s exponential follow, blocks
    public static final double CAMERA_LAG_VERTICAL = .6;                                // vertical lag share (softer jumps)
    public static final double CAMERA_LEAD_SECONDS = .16, CAMERA_LEAD_MAX = .9, CAMERA_LEAD_RATE = 3.5;

    /** Exponential smoothing factor for a frame of {@code dt} seconds at {@code rate} per second (frame-rate independent). */
    public static double follow(double rate, double dt) { return 1 - Math.exp(-rate * Math.max(0, dt)); }

    /** Lead target in blocks from a horizontal velocity in blocks per second, clamped. */
    public static Vec3 leadTarget(Vec3 velocityPerSecond) {
        var flat = new Vec3(velocityPerSecond.x, 0, velocityPerSecond.z).scale(CAMERA_LEAD_SECONDS);
        return flat.length() > CAMERA_LEAD_MAX ? flat.normalize().scale(CAMERA_LEAD_MAX) : flat;
    }

    // ---- R81 trail tone: white core fading to cool spirit blue (Ori's light is white-blue, never warm) ------------
    public static final int TRAIL_CORE = 0xF2F8FF, TRAIL_TAIL = 0x8EC8FF;

    /** Colour of mote {@code i} of {@code n} along the trail: the head is white, the tail cooler blue. */
    public static int trailColor(int i, int n) {
        float t = n <= 1 ? 0 : (float) i / (n - 1);
        int r = (int) (0xF2 + (0x8E - 0xF2) * t), g = (int) (0xF8 + (0xC8 - 0xF8) * t);
        return r << 16 | g << 8 | 0xFF;
    }
}
