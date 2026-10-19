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
}
