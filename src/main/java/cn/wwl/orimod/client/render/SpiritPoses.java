package cn.wwl.orimod.client.render;

import static cn.wwl.orimod.client.render.SpiritPoseLayer.*;

/**
 * R95 pose overrides (playtest: "go deep on pose overrides" for run, swim, glide/elytra, held items). Angles are radians
 * about the standing rest pose's model axes (see {@link SpiritPoseLayer}); every pose is authored for the -X ("_l") side
 * and mirrored for "_r". The "_l" bones are the player's right hand (model faces +Z, -X is its right).
 * Previewed offline with tools/pose (shots/r95-pose-*.png) before tuning in game.
 * <ul>
 * <li>Swim: an elongated body (the whole model lies along the swim direction, SpiritPlayerVisual), soft paddling arms
 * (in on the pull, out and a little forward on the reach), a small flutter kick with pointed feet, head up to look ahead,
 * ears / feelers / tail trailing back along the body. {@code dart} (water dash) tucks the arms to the sides and the legs
 * together: a pointed dart, not a humanoid dog-paddle.</li>
 * <li>Glide (spirit glide and elytra): arms raised in a soft wide V a little ahead of the head (Kuro's feather), legs
 * trailing back slightly apart with soft knees and pointed feet, chest a little open; the torso tilts into the airflow
 * (body pitch, SpiritPlayerVisual).</li>
 * <li>Cradle: a held tool is carried close to the chest, elbow bent, wrist turned in, instead of an outstretched arm.</li>
 * <li>Throw ready: a held trident / throwable is cocked by the shoulder, elbow down, close to the head (no vanilla
 * two-hand stretch).</li>
 * </ul>
 */
final class SpiritPoses {
    private SpiritPoses() {}

    /** Adds a pose for the -X side bone and its mirror (+X) with the same weight. */
    private static void pair(SpiritPoseLayer l, int left, float x, float y, float z, float w) {
        l.add(left, x, y, z, w); l.addMirrored(left + 1, x, y, z, w);
    }

    /** @param phase stroke phase (rad); {@code dart} 0..1 water-dash tuck; {@code cruise} 0..1 (0 = floating in place). */
    static void swim(SpiritPoseLayer l, float phase, float dart, float cruise, float w) {
        if (w <= 0) return;
        float s = (float) Math.sin(phase), c = (float) Math.cos(phase), open = 1 - dart;
        float reach = .5f + .5f * s;
        // Arms: angle out from the body (Z from the T) is smallest on the pull, wider on the reach; the recovery brings
        // them a little forward. Floating paddles wider and slower.
        float out = 1.25f - (.5f + .2f * (1 - cruise)) * open * reach + .2f * dart;
        float fwd = -.35f * open * Math.max(0, c) - .15f * (1 - cruise) * open + .12f * dart;
        l.add(NECK, -.5f * (1 - .3f * dart), 0, 0, w); l.add(HEAD, -.45f, 0, 0, w);
        l.add(UPPER_ARM_L, fwd, 0, out, w); l.addMirrored(UPPER_ARM_R, fwd, 0, out, w);
        float elbow = .55f * (.5f + .5f * c) * open;
        l.add(FOREARM_L, 0, elbow, 0, w); l.addMirrored(FOREARM_R, 0, elbow, 0, w);
        float kick = .28f * open * (.6f + .4f * cruise);
        l.add(THIGH_L, kick * s, 0, .05f * dart, w); l.addMirrored(THIGH_R, -kick * s, 0, .05f * dart, w);
        l.add(KNEE_L, .18f + .18f * Math.max(0, s) * open, 0, 0, w); l.add(KNEE_R, .18f + .18f * Math.max(0, -s) * open, 0, 0, w);
        pair(l, ANKLE_L, .6f + .2f * dart, 0, 0, w);
        // Appendages trail back along the body (standing "down" is behind once the body lies along the swim direction).
        l.add(TAIL, -1.3f, 0, 0, w);
        l.add(EAR_L, -.85f - .2f * dart, 0, 0, w); l.add(EAR_R, -.85f - .2f * dart, 0, 0, w);
        l.add(FEELER_L, -.9f, 0, 0, w); l.add(FEELER_R, -.9f, 0, 0, w);
    }

    /** Spirit glide / elytra. {@code sway} -1..1 slow drift so the held pose breathes. */
    static void glide(SpiritPoseLayer l, float sway, float w) {
        if (w <= 0) return;
        l.add(NECK, -.18f, 0, 0, w); l.add(HEAD, -.15f, 0, 0, w); l.add(SPINE, -.1f, 0, 0, w);
        l.add(UPPER_ARM_L, 0, .38f, -.62f + .05f * sway, w); l.addMirrored(UPPER_ARM_R, 0, .38f, -.62f - .05f * sway, w);
        pair(l, FOREARM_L, 0, .35f, -.15f, w);
        l.add(THIGH_L, .3f + .05f * sway, 0, -.14f, w); l.addMirrored(THIGH_R, .3f - .05f * sway, 0, -.14f, w);
        pair(l, KNEE_L, .45f, 0, 0, w); pair(l, ANKLE_L, .55f, 0, 0, w);
    }

    /** Held tool cradled at the chest; {@code side} 0 = "_l" bones (player's right hand), 1 = "_r". */
    static void cradle(SpiritPoseLayer l, int side, float bob, float w) {
        if (w <= 0) return;
        if (side == 0) {
            l.add(UPPER_ARM_L, -.55f, 0, 1.25f, w); l.add(FOREARM_L, 0, 1.85f, -.2f + bob, w); l.add(HAND_L, 0, .25f, -.2f, w);
        } else {
            l.addMirrored(UPPER_ARM_R, -.55f, 0, 1.25f, w); l.addMirrored(FOREARM_R, 0, 1.85f, -.2f + bob, w); l.addMirrored(HAND_R, 0, .25f, -.2f, w);
        }
    }

    /** One-handed weapons rest beside the thigh, with a soft elbow and a small forward reach. */
    static void lowered(SpiritPoseLayer l, int side, float bob, float w) {
        if (w <= 0) return;
        if (side == 0) {
            l.add(UPPER_ARM_L, -.12f + bob * .3f, 0, 1.42f, w);
            l.add(FOREARM_L, 0, .15f, 0, w); l.add(HAND_L, 0, 0, 0, w);
        } else {
            l.addMirrored(UPPER_ARM_R, -.12f + bob * .3f, 0, 1.42f, w);
            l.addMirrored(FOREARM_R, 0, .15f, 0, w); l.addMirrored(HAND_R, 0, 0, 0, w);
        }
    }

    static void held(SpiritPoseLayer l, int side, float bob, float lowered) {
        cradle(l, side, bob, 1 - lowered); lowered(l, side, bob, lowered);
    }

    /** Wrist on the cocked arm: tips the shaft forward over the shoulder (about the axis that is model X once cocked,
     *  measured offline: hand local X/Y/Z map to world X with weights .47/.5/.73, so 1.1 rad is split that way). */
    private static final float[] HAND_TILT = {.52f, .3f + .55f, .8f};

    /** Trident / throwable cocked by the shoulder while charging. */
    static void throwReady(SpiritPoseLayer l, int side, float w) {
        if (w <= 0) return;
        if (side == 0) {
            l.add(UPPER_ARM_L, 0, -.75f, -.75f, w); l.add(FOREARM_L, 0, 1.9f, 0, w); l.add(HAND_L, HAND_TILT[0], HAND_TILT[1], HAND_TILT[2], w);
        } else {
            l.addMirrored(UPPER_ARM_R, 0, -.75f, -.75f, w); l.addMirrored(FOREARM_R, 0, 1.9f, 0, w); l.addMirrored(HAND_R, HAND_TILT[0], HAND_TILT[1], HAND_TILT[2], w);
        }
    }

    /**
     * Run extras, added on top of the run clip (additive): chest forward with the head kept level, and a high knee on each
     * leg's forward swing. The run clip has knee_l highest at phase .75 and knee_r at .25 (measured from ori.glb).
     */
    static void run(SpiritPoseLayer l, float phase, float w) {
        if (w <= 0) return;
        float k = cn.wwl.orimod.movement.FeelTuning.RUN_KNEE_LIFT, f = cn.wwl.orimod.movement.FeelTuning.RUN_KNEE_FOLD;
        l.add(SPINE, cn.wwl.orimod.movement.FeelTuning.RUN_CHEST_LEAN, 0, 0, w);
        l.add(NECK, -.08f, 0, 0, w); l.add(HEAD, -.07f, 0, 0, w);
        float liftL = lift(phase - .75f), liftR = lift(phase - .25f);
        l.add(THIGH_L, -k * liftL, 0, 0, w); l.add(KNEE_L, f * liftL, 0, 0, w);
        l.add(THIGH_R, -k * liftR, 0, 0, w); l.add(KNEE_R, f * liftR, 0, 0, w);
    }

    /** Smooth bump: 1 at {@code d} = 0 (mod 1), 0 beyond a quarter cycle away. */
    static float lift(float d) {
        d -= (float) Math.floor(d + .5f);
        float c = (float) Math.cos(d * Math.PI * 2);
        return c <= 0 ? 0 : c * c;
    }
}
