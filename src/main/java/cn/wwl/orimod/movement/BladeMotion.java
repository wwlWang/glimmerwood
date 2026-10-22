package cn.wwl.orimod.movement;

import cn.wwl.orimod.combat.BladeArc;
import cn.wwl.orimod.combat.CombatView;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

/** Local, four-tick displacement, added AFTER normal steering/braking. Never fed back into that curve. */
public final class BladeMotion {
    public static final int STEP_TICKS = 4, HOLD_TICKS = 3;
    private int actionId = -1, groundTicks, holdTicks, airHolds;
    private Vec3 step = Vec3.ZERO;

    public static double distance(int stage) { return stage == 3 ? .6 : .4; }

    /** Returns true for a new airborne impulse (which, unlike last tick's velocity, has not seen friction). */
    public boolean observe(CombatView view, boolean enabled, boolean grounded, float yaw) {
        if (grounded) { airHolds = 0; holdTicks = 0; }
        if (!enabled || !view.mode().equals("blade")) { cancel(); actionId = view.actionId(); return false; }
        if (view.actionId() == actionId) return false;
        actionId = view.actionId(); cancel();
        if (grounded) {
            groundTicks = STEP_TICKS;
            step = BladeArc.facing(yaw).scale(distance(view.stage()) / STEP_TICKS);
        } else if (airHolds < BladeArc.AIR_HOLDS) {
            airHolds++; holdTicks = HOLD_TICKS;
            return true;
        }
        return false;
    }

    /** Validate the entire remaining displacement including ordinary movement, then recheck next tick. */
    public Vec3 ground(Vec3 walking, boolean grounded, Predicate<Vec3> supported) {
        if (!grounded) groundTicks = 0;
        if (groundTicks == 0) return Vec3.ZERO;
        if (!supported.test(walking.add(step).scale(groundTicks))) { groundTicks = 0; return Vec3.ZERO; }
        groundTicks--;
        return step;
    }

    /** Do not let vertical shaping immediately undo the server's short fall-speed cap. */
    public double vertical(double shapedVelocity) {
        if (holdTicks == 0) return shapedVelocity;
        holdTicks--;
        return Math.max(shapedVelocity, BladeArc.AIR_HOLD_FALL);
    }

    public void cancel() { groundTicks = holdTicks = 0; step = Vec3.ZERO; }
    public int groundTicks() { return groundTicks; }
    public int holdTicks() { return holdTicks; }
}
