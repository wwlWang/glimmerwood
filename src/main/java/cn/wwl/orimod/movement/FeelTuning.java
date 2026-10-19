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
    // R93: WotW sets horizontal speed to 0 the first frame input is gone (jxvd Ground Movement).
    public static final double GROUND_DECEL = TOP_SPEED; // top -> 0 in 1 tick
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
    // R95: a touch more forward lean at speed (light athletic run, chest over the knees).
    public static final float LEAN_SPEED = .24f, LEAN_ACCEL = .018f, LEAN_MAX = .38f;    // rad, rad per m/s^2
    /** R88 air posture: lean with horizontal speed, nose up while rising and diving forward while falling (rad per b/t). */
    public static final float AIR_LEAN_SPEED = .12f, AIR_LEAN_VERTICAL = .35f;
    /** R88: vertical bounce of the run (squash target swing, two per stride) and stretch with vertical speed in the air. */
    // R93: stronger run cadence + asymmetric air squash (stretch rising, tuck falling) to match WotW squash-stretch.
    public static final float GAIT_BOUNCE = .07f, AIR_STRETCH = .2f, AIR_STRETCH_MAX = .12f;
    public static final float AIR_TUCK = .22f, AIR_TUCK_MAX = .14f;
    /** R88: continuous rise/apex/fall blend by vertical speed (b/t) instead of hard-switched clips. */
    public static final float AIR_RISE_FROM = .02f, AIR_RISE_TO = .22f, AIR_FALL_FROM = .02f, AIR_FALL_TO = .3f;
    public static final float ROLL_PER_TURN = .0011f, ROLL_MAX = .3f;                  // rad per (deg/s * m/s / 6.4)
    public static final float SQUASH_JUMP = 2.6f, SQUASH_LAND = 7.5f;                  // spring velocity impulses (R93 stronger land)
    public static final float SQUASH_MIN = .68f, SQUASH_MAX = 1.32f;
    public static final float BREATH_PERIOD = 3.2f, BREATH_AMPLITUDE = .03f;
    /** Damped springs: stiffness (1/s^2) and damping (1/s); damping below 2*sqrt(stiffness) overshoots. */
    public static final float POSE_STIFFNESS = 180, POSE_DAMPING = 20;
    public static final float SQUASH_STIFFNESS = 240, SQUASH_DAMPING = 11;
    public static final float EAR_STIFFNESS = 120, EAR_DAMPING = 9;
    public static final float EAR_TURN = .0016f, EAR_FALL = .55f, EAR_MAX = .6f;
    // ---- R92 secondary motion: multi-segment ears (3), feelers (3) and tail (5), see SpringChain ---------------
    /** Per-segment spring stiffness (1/s^2) / damping (1/s) / forcing gain, root to tip. Softer toward the tip = more lag. */
    // R93: softer tips, higher gain/limits/kicks so follow-through reads on camera (playtest still called ears/tail stiff).
    public static final float[] EAR_CHAIN_K = {130, 85, 48}, EAR_CHAIN_C = {9, 7, 5.2f}, EAR_CHAIN_G = {18, 17, 15};
    public static final float[] FEELER_CHAIN_K = {170, 110, 68}, FEELER_CHAIN_C = {10, 8, 6}, FEELER_CHAIN_G = {16, 15, 13};
    public static final float[] TAIL_CHAIN_K = {100, 78, 58, 42, 30}, TAIL_CHAIN_C = {8, 7, 6, 5, 4.2f}, TAIL_CHAIN_G = {11, 11, 10.5f, 10, 9.5f};
    public static final float EAR_CHAIN_LIMIT = .72f, FEELER_CHAIN_LIMIT = .62f, TAIL_CHAIN_LIMIT = .58f;
    /** Forcing normalisers: one "unit" of force is this much body motion (m/s^2, m/s, rad/s). */
    public static final float SEC_ACCEL_UNIT = 20, SEC_VERTICAL_ACCEL_UNIT = 26, SEC_FALL_UNIT = 12, SEC_TURN_UNIT = 6, SEC_FORCE_MAX = 2.0f;
    /** Airflow drag: ears and tail stream back with forward speed (units per m/s / 6.4). */
    public static final float SEC_DRAG = 1.15f;   // R95: ears stream back harder at a run (R93 .9)
    /** Idle travelling sway amplitude (force units) and rate (rad/s); tail also swings with the stride. */
    public static final float SEC_IDLE_SWAY = .28f, SEC_IDLE_RATE = 2.1f, SEC_TAIL_GAIT_SWAY = .75f;
    /** Landing flop and take-off flick (spring velocity impulse, rad/s, scaled by impact). */
    public static final float SEC_LAND_KICK = 4.8f, SEC_JUMP_KICK = 2.4f;
    /** R93: procedural thigh/knee tuck (rad). Fall tucks knees; land kicks a crouch; rise slightly straightens. */
    public static final float LEG_TUCK_FALL = .7f, LEG_TUCK_MAX = .55f, LEG_STRETCH = .25f, LEG_STRETCH_MAX = .18f;
    public static final float LEG_LAND_KICK = 3.5f, LEG_THIGH_SHARE = .55f;
    public static final float LEG_STIFFNESS = 220, LEG_DAMPING = 16;
    /** Ori silhouette: the large ears rest swept up and back into a V (rad, base/mid/tip) instead of hanging flat. */
    public static final float[] EAR_REST_LIFT = {.62f, .18f, -.08f};
    public static final float EAR_REST_SPREAD = .16f;
    /** Tail rest curve about the pitch axis (rad, base to tip): droop at the root, curl up toward the tip. */
    public static final float[] TAIL_REST_LIFT = {-.38f, -.05f, .14f, .2f, .22f};
    /** Feelers rest swept back over the head (rad about pitch, negative = back), base to tip. */
    public static final float[] FEELER_REST_PITCH = {-.5f, -.18f, -.08f};
    /** Ori's ready stance: the body dips forward a little when idle (rad). */
    public static final float READY_LEAN = .12f;

    // ---- R94 animation polish ------------------------------------------------------------------------------
    /** Land into run: touchdown above this speed (m/s) keeps the run going and blends a short leg absorb instead. */
    public static final float LAND_RUN_SPEED = 2.2f, LAND_RUN_TIME = .26f, LAND_RUN_BLEND = .7f, LAND_RUN_LEAN_KICK = 2.6f;
    /** Gait knee depth: extra share of the authored leg bend (thigh, knee, ankle) at walk / run / sprint, capped (rad). */
    public static final float GAIT_GAIN_WALK = .22f, GAIT_GAIN_RUN = .5f, GAIT_GAIN_SPRINT = .18f, GAIT_EXTRA_MAX = .6f;   // R95 run .38 -> .5
    public static final float[] GAIT_BONE_GAIN = {1f, 1f, .9f};   // thigh, knee, ankle (R95 thigh .8 -> 1)
    /** Mantle pose: forward pitch over the lip (rad), knee tuck target (rad). */
    public static final float MANTLE_LEAN = .55f, MANTLE_TUCK = .5f;
    /** Weapon swings: torso twist (rad) that coils during the windup and whips through the hit; hit lean kick. */
    /** Clip share where the authored blade swings have the arm raised highest / have struck through (measured from ori.glb). */
    public static final float SWING_RAISE_AT = .24f, SWING_STRIKE_AT = .45f;
    public static final float SWING_TWIST = .34f, SWING_LEAN_KICK = 1.6f, SWING_EAR_FLICK = 1.8f;
    /** Hammer: back arch while wound up (rad), breathing share, slam pitch (rad) on release. */
    public static final float HAMMER_ARCH = .26f, HAMMER_BREATH = .18f, HAMMER_SLAM = .42f;
    /** Spear: ready twist while held (rad), throw whip (rad). */
    public static final float SPEAR_READY_TWIST = .14f, SPEAR_THROW_TWIST = .5f;

    // ---- R95 pose overrides (client/render/SpiritPoses, SpiritPoseLayer) --------------------------------------------
    /** Run extras on top of the run clip: chest lean (rad), thigh lift and knee fold on each leg's forward swing (rad). */
    public static final float RUN_CHEST_LEAN = .12f, RUN_KNEE_LIFT = .42f, RUN_KNEE_FOLD = .5f;
    /** Swim: paddle stroke rate (Hz) floating / cruising; body pitch when floating (rad, 0 = upright, pi/2 = lying along
     *  the swim direction); 3D speed (m/s) where the water-dash dart tuck starts / is full; drop of the lying body (blocks). */
    public static final float SWIM_STROKE_FLOAT = .6f, SWIM_STROKE_CRUISE = 1.05f, SWIM_FLOAT_PITCH = 1.0f;
    public static final float SWIM_CRUISE_FROM = .8f, SWIM_CRUISE_TO = 2.6f, SWIM_DART_FROM = 11.5f, SWIM_DART_TO = 15f, SWIM_DROP = .22f;
    /** Spirit glide torso tilt into the airflow (rad). Elytra lies along the view like vanilla. */
    public static final float GLIDE_TILT = .42f;
    /** Whole-body pitch spring (swim / glide / elytra) and pose fade-in times (s). */
    public static final float BODY_PITCH_STIFFNESS = 70, BODY_PITCH_DAMPING = 15, POSE_FADE = .18f, HOLD_FADE = .15f;

    /** R95: 0 (floating in place) .. 1 (cruising) from the 3D swim speed in m/s. */
    public static float swimCruise(float speed) { return smoothstep(SWIM_CRUISE_FROM, SWIM_CRUISE_TO, speed); }
    /** R95: water-dash dart tuck share from the 3D speed in m/s (a cruise tops out near 11 m/s, a dash bursts to 19). */
    public static float swimDart(float speed) { return smoothstep(SWIM_DART_FROM, SWIM_DART_TO, speed); }
    /**
     * R95: body pitch (rad) for a swimmer moving with velocity (horizontal {@code h}, vertical {@code vy}, m/s): lies along
     * the motion when cruising ({@code pi/2} level, more nose-down when diving), stands up toward {@link #SWIM_FLOAT_PITCH}
     * when floating in place.
     */
    public static float swimPitch(float h, float vy, float cruise) {
        float along = (float) (Math.PI / 2 + Math.max(-1.2, Math.min(1.2, Math.atan2(-vy, Math.max(.3f, h)))));
        return SWIM_FLOAT_PITCH + (along - SWIM_FLOAT_PITCH) * cruise;
    }

    /**
     * R94: remaps a swing's linear progress {@code k} (0..1) so the authored clip anticipates, snaps through the hit and
     * eases out: the windup share of the timeline covers the clip up to its raise peak ({@link #SWING_RAISE_AT}, where
     * blade_1..3 have the arm highest), the hit share races to {@link #SWING_STRIKE_AT}, recovery eases into the end.
     */
    public static float swingCurve(float k, float windup, float hitEnd) {
        k = Math.max(0, Math.min(1, k));
        windup = Math.max(.01f, Math.min(.9f, windup)); hitEnd = Math.max(windup + .01f, Math.min(.99f, hitEnd));
        float r = SWING_RAISE_AT, h = SWING_STRIKE_AT;
        if (k < windup) { float t = k / windup; return r * t * t * (3 - 2 * t); }
        if (k < hitEnd) { float t = (k - windup) / (hitEnd - windup); return r + (h - r) * (1 - (1 - t) * (1 - t)); }
        float t = (k - hitEnd) / (1 - hitEnd);
        return h + (1 - h) * (1 - (1 - t) * (1 - t) * (1 - t));
    }

    /** R94: torso twist share for a swing at progress {@code k}: coil back (negative) in the windup, whip through, settle. */
    public static float swingTwist(float k, float windup, float hitEnd) {
        k = Math.max(0, Math.min(1, k));
        windup = Math.max(.01f, Math.min(.9f, windup)); hitEnd = Math.max(windup + .01f, Math.min(.99f, hitEnd));
        if (k < windup) { float t = k / windup; return -.6f * t * t * (3 - 2 * t); }
        if (k < hitEnd) { float t = (k - windup) / (hitEnd - windup); return -.6f + 1.6f * t * t * (3 - 2 * t); }
        float t = (k - hitEnd) / (1 - hitEnd);
        return 1 - t * t * (3 - 2 * t);
    }

    /** R94: weight of the land-absorb layer {@code seconds} after a land into run (1 at contact, 0 at LAND_RUN_TIME). */
    public static float landRunWeight(float seconds) {
        if (seconds < 0 || seconds >= LAND_RUN_TIME) return 0;
        float t = 1 - seconds / LAND_RUN_TIME;
        return t * t;
    }

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

    /**
     * R93: Y-scale target for the squash spring from vertical speed in blocks/tick.
     * Rising stretches above 1; falling tucks below 1 (Ori leaps long then pulls the legs in).
     */
    public static float airScale(float vy) {
        if (vy >= 0) return 1 + Math.min(AIR_STRETCH_MAX, vy * AIR_STRETCH);
        return 1 - Math.min(AIR_TUCK_MAX, -vy * AIR_TUCK);
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
