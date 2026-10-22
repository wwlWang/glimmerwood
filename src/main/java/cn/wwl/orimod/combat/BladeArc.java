package cn.wwl.orimod.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spirit Edge as a wide arc rather than a narrow poke (user feedback after R108: "砍起来不像 Ori 的范围斩击"). Each
 * stage sweeps a horizontal fan around the facing: stages 1 and 2 cross in opposite directions, the finisher is a
 * wider, longer overhead chop. Pure rules shared by the server hit test, the client slash and the tests.
 */
public final class BladeArc {
    private static final double[] REACH = {0, 3.4, 3.4, 3.8};
    private static final double[] HALF_ANGLE = {0, Math.toRadians(75), Math.toRadians(75), Math.toRadians(90)};
    private static final double[] KNOCKBACK = {0, .35, .35, .7};
    /** Vertical reach above and below the chest line. */
    public static final double VERTICAL = 1.6;
    /** Upward lift the finisher gives a grounded target. */
    public static final double FINISHER_LIFT = .28;
    /** Mid-air slashes that may hold the player up before the next landing. */
    public static final int AIR_HOLDS = 3;
    /** Downward speed an air slash caps the fall at (blocks/tick). */
    public static final double AIR_HOLD_FALL = -.04;
    private BladeArc() {}

    private static int clamp(int stage) { return Math.clamp(stage, 1, 3); }
    public static double reach(int stage) { return REACH[clamp(stage)]; }
    public static double halfAngle(int stage) { return HALF_ANGLE[clamp(stage)]; }
    public static double knockback(int stage) { return KNOCKBACK[clamp(stage)]; }
    /** +1 sweeps from the left of the facing to the right, -1 the other way; the finisher falls from overhead. */
    public static int sweep(int stage) { return clamp(stage) == 2 ? -1 : 1; }
    public static boolean overhead(int stage) { return clamp(stage) == 3; }

    /** Horizontal unit facing for a yaw in degrees (Minecraft convention: yaw 0 faces +Z). */
    public static Vec3 facing(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** The point of {@code box} nearest to {@code origin}. */
    public static Vec3 closest(Vec3 origin, AABB box) {
        return new Vec3(Math.clamp(origin.x, box.minX, box.maxX), Math.clamp(origin.y, box.minY, box.maxY),
                Math.clamp(origin.z, box.minZ, box.maxZ));
    }

    /** Whether {@code point} lies in the stage's fan around {@code origin} (the chest) facing {@code yawDegrees}. */
    public static boolean contains(Vec3 origin, float yawDegrees, Vec3 point, int stage) {
        Vec3 offset = point.subtract(origin);
        if (Math.abs(offset.y) > VERTICAL) return false;
        Vec3 flat = new Vec3(offset.x, 0, offset.z);
        double distance = flat.length();
        if (distance > reach(stage)) return false;
        // Inside the body radius everything counts: a target pressed against the player is always in front.
        if (distance < .45) return true;
        return facing(yawDegrees).dot(flat.scale(1 / distance)) >= Math.cos(halfAngle(stage));
    }
}
