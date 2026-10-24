package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;

/** Feather permission and motion are shared by the server, prediction and presentation. */
public final class FeatherRules {
    public static final int MIGRATION_TICKS = 40;
    private FeatherRules() {}
    public static boolean permitted(boolean spirit, boolean glideSkill, boolean feather, boolean flying) {
        return spirit && (glideSkill || feather) && !flying;
    }
    public static Vec3 velocity(Vec3 current, Vec3 wish, boolean upgraded, boolean airControl) {
        double acceleration = upgraded ? FeelTuning.FEATHER_SPIRIT_ACCEL : FeelTuning.FEATHER_ACCEL;
        Vec3 h = new Vec3(current.x, 0, current.z).scale(.92).add(wish.scale(acceleration * (airControl ? 1.25 : 1)));
        double cap = upgraded ? FeelTuning.FEATHER_SPIRIT_SPEED : FeelTuning.FEATHER_SPEED;
        // Old FallFlying saves shed excess speed over several ticks instead of stopping in mid-air.
        double limit = Math.max(cap, current.horizontalDistance() * .86);
        if (h.lengthSqr() > limit * limit) h = h.normalize().scale(limit);
        return new Vec3(h.x, Math.max(current.y, upgraded ? FeelTuning.FEATHER_SPIRIT_FALL : FeelTuning.FEATHER_FALL), h.z);
    }
}
