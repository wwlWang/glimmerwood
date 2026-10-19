package cn.wwl.orimod.movement;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared deterministic rules. Callers supply observations and own all world/state mutations. */
public final class MovementRules {
    public static final int INPUT_BUFFER_TICKS = 2, COYOTE_TICKS = 2, DASH_TICKS = 5, DASH_COOLDOWN_TICKS = 12;
    public static final double DASH_SPEED = 1.12, WALL_SLIDE_SPEED = -.12, WALL_JUMP_SPEED = .47;
    public static final double GLIDE_FALL_SPEED = -.12, GRAPPLE_SPEED = .85, STOMP_SPEED = -2.4;
    // Input release, collision and invalid player states still cancel locally without waiting.
    public static final int GLIDE_LEASE_TICKS = 40;
    /** R38 Launch: charge needed on the ground (crouch held), and speeds of the charged ground and the one air Launch. */
    public static final int LAUNCH_CHARGE_TICKS = 10;
    /** R47: Launch contact damage (WotW deals 5 on contact) for this many ticks after take-off, once per target. */
    public static final int LAUNCH_HIT_TICKS = 10;
    public static final float LAUNCH_DAMAGE = 5;
    /** R40: updraft lift while gliding: added per tick, and the rising speed cap. */
    public static final double UPDRAFT_ACCEL = .09, UPDRAFT_MAX = .5;
    /** R40: with the Triple Jump shard, air jumps are spent only after the double and the triple jump. */
    public static boolean airJumpsSpent(boolean doubleSpent, boolean tripleShard, boolean tripleSpent) { return doubleSpent && (!tripleShard || tripleSpent); }
    public static double updraftVelocity(double vy) { return Math.min(UPDRAFT_MAX, Math.max(vy, GLIDE_FALL_SPEED) + UPDRAFT_ACCEL); }
    public static final double LAUNCH_GROUND_SPEED = 1.6, LAUNCH_AIR_SPEED = 1.3, LAUNCH_MIN_UP = .55;
    private MovementRules() {}

    /** Launch direction: the view, lifted so a Launch always rises at least {@link #LAUNCH_MIN_UP}. */
    public static Vec3 launchVelocity(float yaw, float pitch, boolean grounded) {
        Vec3 view = Vec3.directionFromRotation(pitch, yaw);
        Vec3 dir = view.y >= LAUNCH_MIN_UP ? view : new Vec3(view.x, 0, view.z).normalize().scale(Math.sqrt(1 - LAUNCH_MIN_UP * LAUNCH_MIN_UP)).add(0, LAUNCH_MIN_UP, 0);
        if (!Double.isFinite(dir.x) || new Vec3(view.x, 0, view.z).lengthSqr() < 1e-6 && view.y < LAUNCH_MIN_UP) dir = new Vec3(0, 1, 0);
        return dir.normalize().scale(grounded ? LAUNCH_GROUND_SPEED : LAUNCH_AIR_SPEED);
    }

    /** Ground Launch needs a full charge; the air Launch is once per airtime. */
    /** R88: holding the press that started a jump never turns into a glide, so the fall keeps its gravity; re-press to glide. */
    public static boolean glideArmed(long jumpEdgeAt, long jumpPerformedAt) { return jumpEdgeAt > jumpPerformedAt; }

    /** R88: crouch + jump becomes Launch only when Launch would actually fire; otherwise it is an ordinary jump. */
    public static boolean crouchLaunches(boolean crouching, boolean unlocked, boolean grounded, int chargeTicks, boolean airLaunchSpent) {
        return crouching && launchAllowed(unlocked, grounded, chargeTicks, airLaunchSpent);
    }

    public static boolean launchAllowed(boolean unlocked, boolean grounded, int chargeTicks, boolean airLaunchSpent) {
        if (!unlocked) return false;
        return grounded ? chargeTicks >= LAUNCH_CHARGE_TICKS : !airLaunchSpent;
    }

    public static MoveMode jumpMode(boolean groundCharge, long pressedAt, long groundedAt,
                                    boolean grounded, boolean wall, boolean wallReady,
                                    boolean airJumpSpent, boolean blocked) {
        if (blocked) return MoveMode.IDLE;
        if (groundCharge && pressedAt - groundedAt <= COYOTE_TICKS) return MoveMode.JUMP;
        if (!grounded && wall && wallReady) return MoveMode.WALL_JUMP;
        if (!grounded && !airJumpSpent) return MoveMode.DOUBLE_JUMP;
        return MoveMode.IDLE;
    }

    public static Vec3 inputDirection(float forward, float left, float yawDegrees, boolean fallback) {
        float yaw = yawDegrees * Mth.DEG_TO_RAD;
        Vec3 direction = new Vec3(left * Mth.cos(yaw) - forward * Mth.sin(yaw), 0,
                forward * Mth.cos(yaw) + left * Mth.sin(yaw));
        if (direction.lengthSqr() < .01) return fallback ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)) : Vec3.ZERO;
        return direction.normalize();
    }

    public static Vec3 wallJumpVelocity(Vec3 wallNormal, double jumpY) {
        Vec3 away = wallNormal.scale(-WALL_JUMP_SPEED);
        return new Vec3(away.x, Math.max(.5, jumpY), away.z);
    }

    /** Both initial authorization and subsequent client ticks preserve downward momentum. */
    public static Vec3 dashVelocity(Vec3 horizontalVelocity, double currentY) {
        return new Vec3(horizontalVelocity.x, Math.min(0, currentY), horizontalVelocity.z);
    }

    /** R55 Climb: upward speed while pushing into a wall with the ability learned. */
    public static final double CLIMB_SPEED = .14;
    public static boolean climbing(boolean unlocked, float forward) { return unlocked && forward > .1f; }
    public static Vec3 climbVelocity(Vec3 velocity) { return new Vec3(velocity.x, CLIMB_SPEED, velocity.z); }

    public static Vec3 wallSlideVelocity(Vec3 velocity) {
        return new Vec3(velocity.x, Math.max(velocity.y, WALL_SLIDE_SPEED), velocity.z);
    }

    public static Vec3 glideVelocity(Vec3 velocity, Vec3 direction, boolean enhancedControl) {
        Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z).scale(.92)
                .add(direction.scale(enhancedControl ? .075 : .06));
        if (horizontal.lengthSqr() > .25) horizontal = horizontal.normalize().scale(.5);
        return new Vec3(horizontal.x, Math.max(velocity.y, GLIDE_FALL_SPEED), horizontal.z);
    }

    public static Vec3 grappleVelocity(Vec3 center, Vec3 point) {
        Vec3 offset = point.subtract(center);
        return offset.lengthSqr() < .01 ? Vec3.ZERO : offset.normalize().scale(Math.min(GRAPPLE_SPEED, offset.length()));
    }

    /** A stomp lease starts at -1.2; local travel accelerates to the terminal speed. */
    public static Vec3 stompVelocity(double currentY) {
        return new Vec3(0, Math.max(STOMP_SPEED, currentY - .55), 0);
    }
}
