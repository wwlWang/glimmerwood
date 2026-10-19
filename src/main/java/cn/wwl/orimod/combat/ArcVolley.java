package cn.wwl.orimod.combat;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * R67 Spirit Arc variant (after WotW's upgraded Spirit Arc): a fully charged shot released while sneaking fans out
 * three spirit arrows ({@link #SPREAD} degrees apart) for {@link #COST} energy instead of one.
 */
public final class ArcVolley {
    public static final int ARROWS = 3;
    public static final double SPREAD = 9;
    public static final float COST = 20;

    private ArcVolley() {}

    public static boolean applies(int charge, boolean sneaking, float energy) {
        return charge >= SpiritCombat.BOW_FULL_CHARGE && sneaking && energy >= COST;
    }

    /** @return the centre direction plus one turned left and one right about the vertical axis. */
    public static List<Vec3> directions(Vec3 look) {
        var l = look.normalize();
        return List.of(l, turn(l, SPREAD), turn(l, -SPREAD));
    }

    private static Vec3 turn(Vec3 v, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, v.z * c - v.x * s);
    }
}
