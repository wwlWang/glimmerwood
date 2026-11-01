package cn.wwl.orimod.client.render;

import static cn.wwl.orimod.client.render.SpiritPoseLayer.*;

/** Vanilla HumanoidModel's eased forward stroke, mapped from a hanging arm onto Ori's T-rest rig.
 * The forearm bends about its hinge; the wrist keeps one fixed grip roll under 30 degrees.
 * One continuous pose owns the entire stroke, including both endpoints and the item socket. */
final class SpiritAttackPose {
    private SpiritAttackPose() { }

    static float bodyYaw(float phase, boolean left) {
        float p = Math.clamp(phase, 0, 1);
        // Bound the square-root curve's infinite initial velocity at the idle/attack seam.
        float enter = Math.clamp(p / .075f, 0, 1);
        enter = enter * enter * (3 - 2 * enter);
        return (float) Math.sin(Math.sqrt(p) * Math.PI * 2) * .15f * enter * (left ? -1 : 1);
    }

    static void write(SpiritPoseLayer pose, float phase, boolean left, boolean held, float headPitch, int stage) {
        float p = Math.clamp(phase, 0, 1);
        // Ease only the first three twentieths into the native curve. This retains the hit window
        // while bounding the fast quartic start at software-rendered 20 FPS / 3 TPS.
        if (p < .15f) {
            float t = p / .15f;
            p = .15f * (-.5f * t * t * t + t * t + .5f * t);
        }
        float eased = 1 - (float) Math.pow(1 - p, 4);
        float stroke = (float) Math.sin(eased * Math.PI);
        float recover = (float) Math.sin(p * Math.PI);
        float body = bodyYaw(p, left);
        // A short creature needs a visible weapon arc; the same vanilla curve has a modestly larger shoulder pitch.
        float amplitude = held ? 1.4f + .03f * Math.clamp(stage, 1, 3) : 1.2f;
        float pitch = -.32f - stroke * amplitude
                - recover * (.7f - Math.clamp(headPitch, -.7f, .7f)) * .75f;
        float yaw = body * 2 + (held ? SpiritPoses.LOWERED_YAW * (left ? -1 : 1) : 0);
        float drop = SpiritPoses.LOWERED_DROP - recover * .10f;
        pose.add(SPINE, 0, body, 0, 1);
        if (left) {
            pose.addMirrored(UPPER_ARM_R, pitch, -yaw, drop, 1);
            pose.addMirrored(FOREARM_R, 0, SpiritPoses.LOWERED_ELBOW, 0, 1);
            pose.addMirrored(HAND_R, held ? SpiritPoses.GRIP_ROLL : 0, SpiritPoses.LOWERED_WRIST, 0, 1);
        } else {
            pose.add(UPPER_ARM_L, pitch, yaw, drop, 1);
            pose.add(FOREARM_L, 0, SpiritPoses.LOWERED_ELBOW, 0, 1);
            pose.add(HAND_L, held ? SpiritPoses.GRIP_ROLL : 0, SpiritPoses.LOWERED_WRIST, 0, 1);
        }
    }

    /** Sword stages use their real server hit window; zero is a six-tick vanilla hammer swing.
     * The shoulder sends the hand forward, then wrist flexion carries the tip through horizontal.
     * Flexion is around the hinge, not a long-axis roll or a rotation of the item independently of the paw. */
    static void writeStrike(SpiritPoseLayer pose, float phase, boolean left, int stage) {
        float duration = stage == 0 ? 6 : cn.wwl.orimod.combat.SpiritCombat.duration(stage);
        float hit = stage == 0 ? 2 : cn.wwl.orimod.combat.SpiritCombat.windup(stage);
        float ticks = Math.clamp(phase, 0, 1) * duration, raised = hit * (stage == 3 ? .525f : .4f), recover = hit + (stage == 3 ? 1.5f : 2);
        float pitch, wrist, elbow = SpiritPoses.LOWERED_ELBOW, sweep = 0;
        // The overhead finisher lifts behind the crown, then drives lower with a longer recovery.
        float highPitch = stage == 3 ? -2.05f : -.65f - .04f * stage;
        float hitPitch = stage == 3 ? -.36f : -.65f - .03f * stage;
        float hitWrist = -1.2f - .03f * stage;
        if (ticks < raised) {
            float w = ease(ticks / raised);
            pitch = mix(-.32f, highPitch, w); wrist = mix(SpiritPoses.LOWERED_WRIST, .4f, w);
        } else if (ticks < hit) {
            float w = ease((ticks - raised) / (hit - raised));
            pitch = mix(highPitch, hitPitch, w); wrist = mix(.4f, hitWrist, w);
            elbow = mix(SpiritPoses.LOWERED_ELBOW, 0, w);
            sweep = w;
        } else if (ticks < recover) {
            pitch = hitPitch; wrist = hitWrist; elbow = 0; sweep = 1;
        } else {
            float w = ease((ticks - recover) / (duration - recover));
            pitch = mix(hitPitch, -.32f, w); wrist = mix(hitWrist, SpiritPoses.LOWERED_WRIST, w);
            elbow = mix(0, SpiritPoses.LOWERED_ELBOW, w);
            sweep = 1 - w;
        }
        float body = bodyYaw(phase, left);
        pose.add(SPINE, 0, body, 0, 1);
        float yaw = mix(SpiritPoses.LOWERED_YAW, .6f, sweep) + body * (left ? -1 : 1);
        if (left) {
            pose.addMirrored(UPPER_ARM_R, pitch, yaw, SpiritPoses.LOWERED_DROP, 1);
            pose.addMirrored(FOREARM_R, 0, elbow, 0, 1);
            pose.addMirrored(HAND_R, SpiritPoses.GRIP_ROLL, wrist, 0, 1);
        } else {
            pose.add(UPPER_ARM_L, pitch, yaw, SpiritPoses.LOWERED_DROP, 1);
            pose.add(FOREARM_L, 0, elbow, 0, 1);
            pose.add(HAND_L, SpiritPoses.GRIP_ROLL, wrist, 0, 1);
        }
    }

    /** World time can be corrected by a server time packet between two rendered frames.
     * Align once when an action arrives, then advance with the entity animation clock.
     * A new stage still starts at its reported server phase; this never changes combat timing. */
    static final class Timeline {
        private long key;
        private double anchor, elapsed;
        private boolean active;
        float elapsed(long action, double localTicks, double reportedTicks) {
            if (!active || action != key || localTicks < anchor) {
                active = true; key = action; anchor = localTicks;
                elapsed = Math.max(0, reportedTicks);
            }
            return (float) (elapsed + localTicks - anchor);
        }
        void end() { active = false; }
    }

    private static float ease(float t) { t = Math.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private static float mix(float a, float b, float weight) { return a + (b - a) * weight; }
    /** Chain input can interrupt recovery at tick six. Blend from the actually displayed pose,
     * not from the new clip's idle endpoint; the next wind-up still finishes within its damage window. */
    static final class Transition {
        private final float[] previous = new float[SpiritPoseLayer.BONES.length * 3], from = new float[previous.length];
        private long lastKey;
        private boolean active;
        private float started = -1;
        void apply(SpiritPoseLayer pose, long key, float now) {
            if (active && key != lastKey) {
                System.arraycopy(previous, 0, from, 0, from.length);
                started = now;
            }
            if (started >= 0) {
                float t = Math.clamp((now - started) / .065f, 0, 1);
                float weight = t * t * (3 - 2 * t);
                for (int i=0;i<from.length;i++) pose.angles[i] = from[i] + (pose.angles[i] - from[i]) * weight;
                if (t == 1) started = -1;
            }
            System.arraycopy(pose.angles, 0, previous, 0, previous.length);
            active = true; lastKey = key;
        }
        void end() { active = false; started = -1; }
    }

}
