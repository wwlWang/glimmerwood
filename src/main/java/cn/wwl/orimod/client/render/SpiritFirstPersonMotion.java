package cn.wwl.orimod.client.render;

/**
 * R114: first-person weapon motion for Spirit form, replacing vanilla's generic arm swing. Pure keyframe math in the
 * vanilla first-person arm space (after the item-arm base translation): +X toward the held side's outside, +Y up,
 * -Z away from the camera. Rotations are degrees, applied as Y, then Z, then X to the item (X pitches the tip: negative
 * points it forward). Every curve starts and ends at {@link Pose#REST}, so interrupted actions return smoothly.
 */
public final class SpiritFirstPersonMotion {
    private SpiritFirstPersonMotion() { }

    public record Pose(float x, float y, float z, float pitch, float yaw, float roll) {
        public static final Pose REST = new Pose(0, 0, 0, 0, 0, 0);

        public Pose lerp(Pose to, float t) {
            return new Pose(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t,
                    pitch + (to.pitch - pitch) * t, yaw + (to.yaw - yaw) * t, roll + (to.roll - roll) * t);
        }

        /** Mirror for the left hand: lateral offsets, yaw and roll change sign. */
        public Pose side(boolean left) { return left ? new Pose(-x, y, z, pitch, -yaw, -roll) : this; }

        public Pose plus(float dx, float dy, float dz) { return new Pose(x + dx, y + dy, z + dz, pitch, yaw, roll); }
    }

    static float smooth(float t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    static float easeOut(float t) { t = Math.clamp(t, 0, 1); return 1 - (1 - t) * (1 - t); }
    static float easeIn(float t) { t = Math.clamp(t, 0, 1); return t * t; }

    // Spirit Edge combo: 1 = forehand sweep toward the off side, 2 = backhand sweep back, 3 = overhead downward cut.
    // Poses are checked after the two-bone wrist fit: raw pitch alone can be cancelled by the wrist limit.
    // Pull the first cut sideways, keep the overhead tip farther away, then cut across/down through the hit window.
    private static final Pose[] BLADE_COCK = {REST(),
            new Pose(-.10f, .14f, -.08f, 6, 9, -38),
            new Pose(-.44f, -.10f, -.04f, -82, 68, 22),
            new Pose(-.27f, .34f, -.03f, -88, -97, 8)};
    private static final Pose[] BLADE_END = {REST(),
            new Pose(-.52f, -.12f, -.16f, -82, 72, 22),
            new Pose(.22f, .10f, -.12f, -66, -82, -10),
            new Pose(-.14f, -.28f, -.26f, -112, 0, 0)};
    private static Pose REST() { return Pose.REST; }

    /**
     * One combo stage on the server's timeline: wind-up from {@code from} (the pose shown when the stage began) to the
     * cocked pose, a fast cut through the hit window, a short hold, then recovery to rest.
     */
    public static Pose blade(int stage, float elapsed, int windup, int hit, int recovery, Pose from) {
        stage = Math.clamp(stage, 1, 3);
        Pose cock = BLADE_COCK[stage], end = BLADE_END[stage];
        if (elapsed < windup) return from.lerp(cock, easeOut(elapsed / Math.max(1, windup)));
        if (elapsed < windup + hit) return cock.lerp(end, smooth((elapsed - windup) / hit));
        float hold = recovery * .3f, back = elapsed - windup - hit;
        if (back < hold) return end;
        return end.lerp(Pose.REST, smooth((back - hold) / Math.max(1, recovery - hold)));
    }

    // Hammer: lifted upright overhead, then slammed forward and down. Pitch moves the weapon along the view axis and
    // barely reads on screen, so the lift is mostly height and roll; pitching it back hid the head behind the camera.
    private static final Pose HAMMER_RAISED = new Pose(-.12f, .30f, .08f, 18, 8, 22);
    private static final Pose HAMMER_STRUCK = new Pose(-.18f, -.10f, -.30f, -118, 0, 10);
    /** Held while plunging in an air slam: head down, ready to strike the ground. */
    private static final Pose HAMMER_PLUNGE = new Pose(-.14f, .04f, -.24f, -128, 0, 10);
    public static final float HAMMER_STRIKE_TICKS = 4, HAMMER_RECOVER_TICKS = 10;

    /** Winding up: {@code charge} 0..1 lifts the hammer; a full charge trembles slightly. */
    public static Pose hammerCharge(float charge, float time) {
        var pose = Pose.REST.lerp(HAMMER_RAISED, easeOut(charge));
        return charge >= 1 ? pose.plus(.006f * (float) Math.sin(time * 37), .006f * (float) Math.sin(time * 29), 0) : pose;
    }

    /** After a release (ground smash) or a landing: from the raised pose (or the plunge) to the strike, then rest. */
    public static Pose hammerStrike(float elapsed, boolean fromPlunge) {
        Pose start = fromPlunge ? HAMMER_PLUNGE : HAMMER_RAISED;
        if (elapsed < HAMMER_STRIKE_TICKS) {
            var pose = start.lerp(HAMMER_STRUCK, easeIn(elapsed / HAMMER_STRIKE_TICKS));
            return pose;
        }
        float back = elapsed - HAMMER_STRIKE_TICKS;
        // The impact jolts the arm down a little before it recovers.
        float jolt = back < 2 ? -.05f * (float) Math.sin(Math.PI * back / 2) : 0;
        return HAMMER_STRUCK.lerp(Pose.REST, smooth((back - 2) / (HAMMER_RECOVER_TICKS - 2))).plus(0, jolt, 0);
    }

    /** Falling in an air slam: swing from the raised pose into the plunge over a few ticks, then hold. */
    public static Pose hammerPlunge(float elapsed) {
        return HAMMER_RAISED.lerp(HAMMER_PLUNGE, smooth(elapsed / 3));
    }

    /** Cancelled wind-up (released before full charge): lower back to rest. */
    public static Pose hammerLower(float elapsed, float charge) {
        return Pose.REST.lerp(HAMMER_RAISED, easeOut(charge)).lerp(Pose.REST, smooth(elapsed / 5));
    }

    // Spear: carried level and pointing at the crosshair, drawn back beside the head, whipped forward, then re-forms.
    // The coil stays clear of the near plane (a closer hand fills the view with the forearm).
    private static final Pose SPEAR_CARRY = new Pose(-.04f, .14f, .02f, -84, 10, 0);
    private static final Pose SPEAR_COIL = new Pose(.06f, .20f, .14f, -88, 2, -6);
    private static final Pose SPEAR_THRUST = new Pose(-.16f, .12f, -.44f, -92, 12, 2);
    public static final float SPEAR_COIL_TICKS = 5.5f, SPEAR_THRUST_TICKS = 3.5f;

    public static Pose spearCarry() { return SPEAR_CARRY; }

    /**
     * {@code elapsed} ticks since the throw; {@code cooldown} 0..1 of the item cooldown left. After the thrust the
     * (thrown) spear drops out of view and rises back as its cooldown ends.
     */
    public static Pose spearThrow(float elapsed, float cooldown) {
        if (elapsed < SPEAR_COIL_TICKS) return SPEAR_CARRY.lerp(SPEAR_COIL, easeOut(elapsed / SPEAR_COIL_TICKS));
        float thrust = elapsed - SPEAR_COIL_TICKS;
        if (thrust < SPEAR_THRUST_TICKS) return SPEAR_COIL.lerp(SPEAR_THRUST, easeIn(thrust / SPEAR_THRUST_TICKS));
        float after = thrust - SPEAR_THRUST_TICKS;
        var settle = SPEAR_THRUST.lerp(SPEAR_CARRY, smooth(after / 6));
        return settle.plus(0, -.75f * smooth(Math.min(1, after / 3)) * smooth(cooldown * 1.4f), 0);
    }

    /**
     * Bow: vanilla's own draw pose (its final -45 degree yaw is what aims the arrow at the crosshair), weighted by
     * {@code raise} for the lift and the lowering, with {@code pull} on vanilla's curve and a forward {@code kick} on release.
     */
    public record BowPose(float raise, float pull, float kick) { }
    public static final float BOW_RAISE_TICKS = 4, BOW_RECOIL_TICKS = 6;

    /** Vanilla's draw curve: (t^2 + 2t) / 3 over 20 ticks, clamped to 1. */
    public static float bowPull(float ticks) {
        float t = Math.max(0, ticks) / 20;
        return Math.min(1, (t * t + t * 2) / 3);
    }

    public static BowPose bowDraw(float ticks) { return new BowPose(smooth(ticks / BOW_RAISE_TICKS), bowPull(ticks), 0); }

    /** After the release: a quick forward kick, then the bow lowers out of the aim. */
    public static BowPose bowRelease(float elapsed) {
        float kick = elapsed < 1.5f ? easeOut(elapsed / 1.5f) : 1 - smooth((elapsed - 1.5f) / (BOW_RECOIL_TICKS - 1.5f));
        return new BowPose(1 - smooth((elapsed - 1.5f) / (BOW_RECOIL_TICKS - 1.5f)), 0, kick);
    }
}
