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
    private MovementRules() {}

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
