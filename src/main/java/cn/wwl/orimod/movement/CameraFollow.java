package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;

/** Exact critically damped follow of a sampled target, with no crossing or motion away from that target. */
public record CameraFollow(Vec3 position, Vec3 velocity) {
    public static CameraFollow at(Vec3 position) { return new CameraFollow(position, Vec3.ZERO); }

    public CameraFollow step(Vec3 target, double dt) {
        if (dt <= 0) return this;
        double[] x = axis(position.x, velocity.x, target.x, dt);
        double[] y = axis(position.y, velocity.y, target.y, dt);
        double[] z = axis(position.z, velocity.z, target.z, dt);
        Vec3 next = new Vec3(x[0], y[0], z[0]), lag = next.subtract(target);
        if (lag.length() > FeelTuning.CAMERA_LAG_MAX)
            next = target.add(lag.normalize().scale(FeelTuning.CAMERA_LAG_MAX));
        return new CameraFollow(next, new Vec3(x[1], y[1], z[1]));
    }

    private static double[] axis(double position, double velocity, double target, double dt) {
        double error = position - target, omega = FeelTuning.CAMERA_FOLLOW_OMEGA;
        if (Math.abs(error) < 1e-10) return new double[]{target, 0};
        // A reversal of the player must not keep dragging the camera in the old direction.
        if (error * velocity > 0) velocity = 0;
        double c = velocity + omega * error, decay = Math.exp(-omega * dt);
        double nextError = (error + c * dt) * decay;
        // High incoming velocity can cross a suddenly stationary target, even with critical damping.
        if (error * nextError <= 0) return new double[]{target, 0};
        return new double[]{target + nextError, (velocity - omega * c * dt) * decay};
    }
}
