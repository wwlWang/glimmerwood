package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;

/**
 * Pure first-person weapon choreography, layered in vanilla's held-arm space (after the native
 * {@code applyItemArmTransform}). Every action is a small local pose {@link Pose} that is zero at rest, so idle, equip and
 * view bob stay vanilla; actions start from the pose actually on screen and return to rest.
 * Right-handed values; {@link Pose#matrix(float)} mirrors yaw, roll and the lateral offset for the left hand.
 */
public final class SpiritFirstPersonMotion {
    private SpiritFirstPersonMotion() { }

    // ---- easing -------------------------------------------------------------------------------------------------
    static float clamp01(float t) { return Math.clamp(t, 0, 1); }
    static float smooth(float t) { t = clamp01(t); return t * t * (3 - 2 * t); }
    static float easeOut(float t) { t = 1 - clamp01(t); return 1 - t * t * t; }
    static float easeInQuad(float t) { t = clamp01(t); return t * t; }
    static float easeOutQuad(float t) { t = 1 - clamp01(t); return 1 - t * t; }

    /** Degrees and blocks; zero is vanilla's held pose. Applied as translate * yaw(Y) * roll(Z) * pitch(X) at the hand. */
    record Pose(float yaw, float roll, float pitch, float dx, float dy, float dz) {
        static final Pose REST = new Pose(0, 0, 0, 0, 0, 0);
        Pose mix(Pose to, float w) {
            return new Pose(yaw + (to.yaw - yaw) * w, roll + (to.roll - roll) * w, pitch + (to.pitch - pitch) * w,
                    dx + (to.dx - dx) * w, dy + (to.dy - dy) * w, dz + (to.dz - dz) * w);
        }
        Pose plus(Pose o) { return new Pose(yaw + o.yaw, roll + o.roll, pitch + o.pitch, dx + o.dx, dy + o.dy, dz + o.dz); }
        Pose scale(float k) { return REST.mix(this, k); }
        float distance(Pose o) {
            return Math.abs(yaw - o.yaw) + Math.abs(roll - o.roll) + Math.abs(pitch - o.pitch)
                    + 100 * (Math.abs(dx - o.dx) + Math.abs(dy - o.dy) + Math.abs(dz - o.dz));
        }
        /** {@code side} is +1 for the right hand, -1 for the left: a mirror about the view's vertical plane. */
        Matrix4f matrix(float side) {
            return new Matrix4f().translation(side * dx, dy, dz)
                    .rotateY((float) Math.toRadians(side * yaw))
                    .rotateZ((float) Math.toRadians(side * roll))
                    .rotateX((float) Math.toRadians(pitch));
        }
    }

    /** Remembers the pose on screen so a new action (or a chained stage) starts there instead of snapping. */
    static final class Memory {
        Pose shown = Pose.REST, from = Pose.REST;
        String action = "rest";
        long key;
        float startedAt;
        Pose begin(String next, long nextKey, float now) {
            if (!next.equals(action) || nextKey != key) { from = shown; action = next; key = nextKey; startedAt = now; }
            return from;
        }
        /** Ease back to rest from whatever was last shown; returns REST once settled. */
        Pose settle(float now) {
            if (action.equals("rest")) return Pose.REST;
            begin("settle", 0, now);
            if (now - startedAt >= SETTLE_TICKS) { action = "rest"; return Pose.REST; }
            return SpiritFirstPersonMotion.settle(now - startedAt, from);
        }
        Pose show(Pose pose) { shown = pose; return pose; }
        void reset() { shown = from = Pose.REST; action = "rest"; key = 0; }
    }

    // ---- spirit blade: three-stage combo, synced to the combat timeline and the slash crescent ------------------------
    // Stage 1 cuts high-left to low-right, stage 2 rises low-right back to high-left, the finisher chops from overhead
    // (BladeSlash / BladeArc). Positive roll tips the blade toward the screen centre for the right hand.
    static final Pose[] BLADE_COCK = {
            null,
            new Pose(-11, 17, -15, -.08f, .12f, .035f),
            new Pose(9, -47, -12, .035f, 0, .02f),
            new Pose(1, 13, 3, -.035f, .16f, .045f)};
    static final Pose[] BLADE_CUT = {
            null,
            new Pose(14, -63, -19, .045f, .01f, -.03f),
            new Pose(-9, 25, -27, -.095f, .105f, -.03f),
            new Pose(20, -43, -58, -.012f, -.023f, -.04f)};

    /**
     * Pose at {@code elapsed} combat ticks into {@code stage}, starting from {@code from} (the pose on screen when the
     * stage began, e.g. a chained recovery). Wind-up cocks the blade, the cut lands with the crescent's three-tick sweep,
     * then recovery eases back to rest by {@link cn.wwl.orimod.combat.SpiritCombat#duration}.
     */
    static Pose blade(int stage, float elapsed, Pose from) {
        stage = Math.clamp(stage, 1, 3);
        float sweepStart = cn.wwl.orimod.combat.SpiritCombat.windup(stage) - 1;
        float sweepEnd = sweepStart + 3;
        float end = cn.wwl.orimod.combat.SpiritCombat.duration(stage);
        if (elapsed < sweepStart) return from.mix(BLADE_COCK[stage], smooth(elapsed / sweepStart));
        Pose cocked = sweepStart <= 0 ? from.mix(BLADE_COCK[stage], 1) : BLADE_COCK[stage];
        if (elapsed < sweepEnd) return cocked.mix(BLADE_CUT[stage], easeOut((elapsed - sweepStart) / (sweepEnd - sweepStart)));
        return BLADE_CUT[stage].mix(Pose.REST, smooth((elapsed - sweepEnd) / (end - sweepEnd)));
    }

    // ---- spirit hammer: wind-up while held, heavy strike on release ---------------------------------------------------
    static final Pose HAMMER_WOUND = new Pose(-6, 14, 32, -.03f, .09f, .05f);
    static final Pose HAMMER_IMPACT = new Pose(4, -6, -58, .01f, -.11f, -.10f);
    static final float BLEND_TICKS = 2.5f;
    /** Ticks to hold the cocked hammer after release while the server's strike swing is in flight. */
    static final float STRIKE_WAIT_TICKS = 4;
    /** SpearCharge's minimum raise; a shorter hold is a cancel, not a throw. */
    static final float SPEAR_MIN_RAISE = 4;

    /** {@code ticks} of use; reaches the cocked pose with the hammer's wind-up and holds a slow breath there. */
    static Pose hammerWindUp(float ticks, Pose from) {
        float k = easeOut(ticks / cn.wwl.orimod.item.SpiritHammerItem.WIND_UP_TICKS);
        Pose target = HAMMER_WOUND.scale(k);
        if (k >= 1) target = target.plus(new Pose(0, 0, 1.5f * (float) Math.sin(ticks * .35f), 0, 0, 0));
        return from.mix(target, smooth(ticks / BLEND_TICKS));
    }

    /** Vanilla swing progress 0..1 (six ticks). Accelerates into the ground, holds the impact, then lifts back to rest. */
    static Pose hammerStrike(float swing, Pose from) {
        float p = clamp01(swing);
        // Quadratic, not cubic: still accelerates into the ground but the last frame before impact stays readable.
        if (p < .35f) return from.mix(HAMMER_IMPACT, easeInQuad(p / .35f));
        if (p < .45f) return HAMMER_IMPACT;
        return HAMMER_IMPACT.mix(Pose.REST, smooth((p - .45f) / .55f));
    }

    // ---- spirit spear: forward thrust on attack and on the throw's follow-through --------------------------------------
    static final Pose SPEAR_THRUST = new Pose(-4, -4, -10, -.03f, .02f, -.24f);

    static Pose spearThrust(float swing, Pose from) {
        float p = clamp01(swing);
        if (p < .35f) return from.mix(SPEAR_THRUST, easeOut(p / .35f));
        return SPEAR_THRUST.mix(Pose.REST, smooth((p - .35f) / .65f));
    }

    /** After an action ends early (cancel, slot change): ease from the pose on screen back to rest. */
    static final float SETTLE_TICKS = 4;
    static Pose settle(float since, Pose from) { return from.mix(Pose.REST, smooth(since / SETTLE_TICKS)); }

    // ---- bow / spear use: vanilla's use matrices, eased in and out instead of snapping ----------------------------------
    static final float RAISE_TICKS = 2.5f, LOWER_TICKS = 4;
    static float raiseWeight(float usedTicks) { return easeOut(usedTicks / RAISE_TICKS); }
    static float lowerWeight(float sinceRelease) { return 1 - smooth(sinceRelease / LOWER_TICKS); }

    /** Vanilla BOW pull curve (ItemInHandRenderer, identical to BowItem#getPowerForTime) so the string and arm agree. */
    static float bowPull(float usedTicks) {
        float f = Math.max(0, usedTicks) / 20f;
        return Math.min(1, (f * f + f * 2) / 3);
    }

    /** Native SPEAR pull is linear over ten ticks; an ease-out keeps the same endpoint without the constant creep. */
    static float spearPull(float usedTicks) { return easeOutQuad(Math.max(0, usedTicks) / 10f); }

    /**
     * Vanilla's BOW use transform at {@code weight}: one at full raise (byte-for-byte the native sequence), identity at
     * zero. The tremble term is native too.
     */
    static Matrix4f bowUse(float side, float usedTicks, float pull, float weight) {
        var m = new Matrix4f().translate(side * -.2785682f * weight, .18344387f * weight, .15731531f * weight)
                .rotateX(rad(-13.935f * weight)).rotateY(rad(side * 35.3f * weight)).rotateZ(rad(side * -9.785f * weight));
        if (pull > .1f) m.translate(0, (float) Math.sin((usedTicks - .1f) * 1.3f) * (pull - .1f) * .004f * weight, 0);
        return m.translate(0, 0, pull * .04f * weight).scale(1, 1, 1 + pull * .2f * weight).rotateY(rad(-side * 45 * weight));
    }

    static Matrix4f spearUse(float side, float usedTicks, float pull, float weight) {
        var m = new Matrix4f().translate(side * -.5f * weight, .7f * weight, .1f * weight)
                .rotateX(rad(-55 * weight)).rotateY(rad(side * 35.3f * weight)).rotateZ(rad(side * -9.785f * weight));
        if (pull > .1f) m.translate(0, (float) Math.sin((usedTicks - .1f) * 1.3f) * (pull - .1f) * .004f * weight, 0);
        return m.translate(0, 0, pull * .2f * weight).scale(1, 1, 1 + pull * .2f * weight).rotateY(rad(-side * 45 * weight));
    }

    // ---- vanilla arm + default attack (kept for mining / non-combat swings) ----------------------------------------
    static Matrix4f arm(float side, float equip) { return new Matrix4f().translation(side * .56f, -.52f + equip * -.6f, -.72f); }

    /** ItemInHandRenderer's default held-item swing (translate, applyItemArmTransform, applyItemArmAttackTransform). */
    static Matrix4f vanillaSwing(float side, float equip, float swing) {
        float sq = (float) Math.sqrt(swing);
        var m = new Matrix4f().translation(side * -.4f * (float) Math.sin(sq * Math.PI),
                .2f * (float) Math.sin(sq * Math.PI * 2), -.2f * (float) Math.sin(swing * Math.PI));
        m.mul(arm(side, equip));
        float f = (float) Math.sin(swing * swing * Math.PI), f1 = (float) Math.sin(sq * Math.PI);
        return m.rotateY(rad(side * (45 + f * -20))).rotateZ(rad(side * f1 * -20)).rotateX(rad(f1 * -80)).rotateY(rad(side * -45));
    }

    // ---- sprint bob: keep tips on screen -------------------------------------------------------------------------
    /** Vanilla's bob reaches 0.1 at full ground speed; its lateral sway is what pushes tips past the edges. */
    static final float MAX_BOB = .1f;
    static Pose sprintTuck(float bob) {
        float k = clamp01(bob / MAX_BOB);
        return new Pose(0, 0, 0, -.05f * k, -.02f * k, 0);
    }

    // ---- first-person glow under bloom shaders ----------------------------------------------------------------------
    /** Shader bloom adds a second halo on top of our additive rim; first person sits too close to absorb both. */
    static float fpBloomScale(boolean firstPerson, boolean shaders) { return firstPerson && shaders ? .72f : 1; }

    // ---- hammer air plunge (pre-existing) ---------------------------------------------------------------------------
    static float plungeWeight(boolean falling, float sinceStart, float sinceEnd) {
        return falling ? Math.clamp(sinceStart / 4, 0, 1) : 1 - Math.clamp(sinceEnd / 8, 0, 1);
    }
    static Matrix4f plunge(float weight) {
        weight = Math.clamp(weight, 0, 1);
        return new Matrix4f().translate(0, -.05f * weight, -.08f * weight)
                .rotateX((float) Math.toRadians(-30 * weight));
    }

    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }
}
