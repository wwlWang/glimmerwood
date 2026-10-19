package cn.wwl.orimod.client.render;

import cn.wwl.orimod.movement.FeelTuning;
import cn.wwl.orimod.movement.SpringChain;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R92: spring-damper secondary motion for every segment of Ori's ears (ear_*_1..3), feelers (antenna_*_1..3) and tail
 * (tail_1..5). Playtest: "耳朵/触须/尾巴的多段骨骼以及物理模拟呢？太僵硬了". R74 only bent the first ear joint and two
 * tail joints by a single spring, so they moved as rigid blades. Now each bone gets its own angle from a
 * {@link SpringChain} driven by the body's measured acceleration, turn rate, fall speed and airflow, so a jump, turn or
 * landing travels down the appendage and the tip follows through last.
 * <p>
 * Also carries the Ori silhouette rest offsets: the big ears swept up and back into a V instead of hanging flat.
 * All of it is one procedural additive clip whose drivers read this frame's angles.
 * <p>R93 also drives thigh/knee tuck from fall speed and landing so the silhouette pulls the legs in mid-air
 * and crouches on impact (WotW squash-stretch), instead of only scaling the whole mesh.
 */
final class SpiritSecondary {
    private record Channel(String name, SpringChain chain, String[] bones, Vector3f axis, float sign) {}

    private final List<Channel> channels = new ArrayList<>();
    private final Channel earLiftL, earLiftR, earSwingL, earSwingR, feelPitchL, feelPitchR, feelRollL, feelRollR, tailLift, tailYaw;
    private Vec3 lastVelocity;
    private final Vector3f smoothVelocity = new Vector3f(), smoothAccel = new Vector3f();
    private float lastYaw = Float.NaN, smoothTurn, lastForce;
    private final cn.wwl.orimod.movement.FeelTuning.Spring legTuck = new cn.wwl.orimod.movement.FeelTuning.Spring(0,
            cn.wwl.orimod.movement.FeelTuning.LEG_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.LEG_DAMPING);
    private GltfAnimation clip;
    private float[] values = new float[0];

    SpiritSecondary() {
        var x = new Vector3f(1, 0, 0); var y = new Vector3f(0, 1, 0); var z = new Vector3f(0, 0, 1);
        earLiftL = add("ear_lift_l", ear(), bones("ear_l", 3), x, 1);
        earLiftR = add("ear_lift_r", ear(), bones("ear_r", 3), x, 1);
        earSwingL = add("ear_swing_l", ear(), bones("ear_l", 3), y, 1);
        earSwingR = add("ear_swing_r", ear(), bones("ear_r", 3), y, 1);
        feelPitchL = add("feeler_pitch_l", feeler(), bones("antenna_l", 3), x, 1);
        feelPitchR = add("feeler_pitch_r", feeler(), bones("antenna_r", 3), x, 1);
        feelRollL = add("feeler_roll_l", feeler(), bones("antenna_l", 3), z, 1);
        feelRollR = add("feeler_roll_r", feeler(), bones("antenna_r", 3), z, 1);
        tailLift = add("tail_lift", tail(), bones("tail", 5), x, 1);
        tailYaw = add("tail_yaw", tail(), bones("tail", 5), y, 1);
    }

    private static String[] bones(String prefix, int n) {
        String[] out = new String[n];
        for (int i = 0; i < n; i++) out[i] = prefix + "_" + (i + 1);
        return out;
    }
    private static SpringChain ear() { return new SpringChain(FeelTuning.EAR_CHAIN_K, FeelTuning.EAR_CHAIN_C, FeelTuning.EAR_CHAIN_G, FeelTuning.EAR_CHAIN_LIMIT, .9f); }
    private static SpringChain feeler() { return new SpringChain(FeelTuning.FEELER_CHAIN_K, FeelTuning.FEELER_CHAIN_C, FeelTuning.FEELER_CHAIN_G, FeelTuning.FEELER_CHAIN_LIMIT, .8f); }
    private static SpringChain tail() { return new SpringChain(FeelTuning.TAIL_CHAIN_K, FeelTuning.TAIL_CHAIN_C, FeelTuning.TAIL_CHAIN_G, FeelTuning.TAIL_CHAIN_LIMIT, .7f); }

    private Channel add(String name, SpringChain chain, String[] bones, Vector3f axis, float sign) {
        var c = new Channel(name, chain, bones, axis, sign); channels.add(c); return c;
    }

    /** Builds the additive clip for this model: one driver per bone per channel, plus the ear rest pose and leg tuck. */
    void bind(NodeTable table, Matrix4f[] palette) {
        var drivers = new ArrayList<PoseDriver>();
        int count = 0;
        for (Channel c : channels) count += c.bones.length;
        values = new float[count + REST + LEG];
        int index = 0;
        for (Channel c : channels) for (String bone : c.bones) {
            if (table.slotOfName(bone) < 0) { index++; continue; }
            drivers.add(driver(table, palette, bone, c.axis, index++));
        }
        // Rest V: lift (X) per ear segment and spread (Z, mirrored) on the base joint.
        for (int i = 0; i < 3; i++) {
            if (table.slotOfName("ear_l_" + (i + 1)) >= 0) drivers.add(driver(table, palette, "ear_l_" + (i + 1), new Vector3f(1, 0, 0), count + i));
            if (table.slotOfName("ear_r_" + (i + 1)) >= 0) drivers.add(driver(table, palette, "ear_r_" + (i + 1), new Vector3f(1, 0, 0), count + i));
        }
        if (table.slotOfName("ear_l_1") >= 0) drivers.add(driver(table, palette, "ear_l_1", new Vector3f(0, 0, 1), count + 3));
        if (table.slotOfName("ear_r_1") >= 0) drivers.add(driver(table, palette, "ear_r_1", new Vector3f(0, 0, 1), count + 4));
        for (int i = 0; i < 3; i++) values[count + i] = FeelTuning.EAR_REST_LIFT[i];
        // The ear bones sit at -X (ear_l) / +X (ear_r); a negative roll about +Z lifts the -X side.
        values[count + 3] = -FeelTuning.EAR_REST_SPREAD; values[count + 4] = FeelTuning.EAR_REST_SPREAD;
        // Tail: droops from the base and curls back up toward the tip, like Ori's, instead of a straight rod.
        for (int i = 0; i < 5; i++) if (table.slotOfName("tail_" + (i + 1)) >= 0) {
            drivers.add(driver(table, palette, "tail_" + (i + 1), new Vector3f(1, 0, 0), count + 5 + i));
            values[count + 5 + i] = FeelTuning.TAIL_REST_LIFT[i];
        }
        // Feelers sweep back over the head (Ori's tufts) rather than standing up like rabbit ears.
        for (int i = 0; i < 3; i++) {
            for (String side : new String[] {"antenna_l_", "antenna_r_"})
                if (table.slotOfName(side + (i + 1)) >= 0) drivers.add(driver(table, palette, side + (i + 1), new Vector3f(1, 0, 0), count + 10 + i));
            values[count + 10 + i] = FeelTuning.FEELER_REST_PITCH[i];
        }
        // R93: thigh + knee pitch for air tuck / land crouch (positive = fold forward).
        String[] legs = {"thigh_l", "thigh_r", "knee_l", "knee_r"};
        for (int i = 0; i < legs.length; i++)
            if (table.slotOfName(legs[i]) >= 0) drivers.add(driver(table, palette, legs[i], new Vector3f(1, 0, 0), count + REST + i));
        // R94: gait depth. Each leg bone's authored walk/run bend is sampled here and re-applied, scaled, on top.
        gaitBones = 0;
        for (int i = 0; i < GAIT_BONES.length; i++) {
            int slot = table.slotOfName(GAIT_BONES[i]);
            if (slot < 0) continue;
            drivers.add(new QuatBone(NodeRotation.offsetOf(table, slot), i * 4, this));
            gaitBones++;
        }
        clip = GltfAnimation.procedural("ori_secondary", drivers.toArray(PoseDriver[]::new));
    }

    /** R94 thigh/knee/ankle per side; {@link FeelTuning#GAIT_BONE_GAIN} by pair. */
    static final String[] GAIT_BONES = {"thigh_l", "thigh_r", "knee_l", "knee_r", "ankle_l", "ankle_r"};
    private static final int GAIT_SAMPLES = 32;
    private float[][] walkDelta, runDelta;   // [bone][sample * 4] local rotation delta from rest (x, y, z, w)
    private final float[] gaitQuat = identities(GAIT_BONES.length);
    private int gaitBones;
    private float gaitExtra;

    private static float[] identities(int n) { float[] q = new float[n * 4]; for (int i = 0; i < n; i++) q[i * 4 + 3] = 1; return q; }

    /** Samples the authored leg bend of the walk and run cycles relative to the rest pose (called with {@link #bind}). */
    void bindGait(NodeTable table, GltfAnimation walk, GltfAnimation run) {
        walkDelta = sampleDeltas(table, walk); runDelta = sampleDeltas(table, run);
    }

    private static float[][] sampleDeltas(NodeTable table, GltfAnimation clip) {
        float[][] out = new float[GAIT_BONES.length][GAIT_SAMPLES * 4];
        float[] scratch = table.newScratch();
        var rest = new Quaternionf(); var q = new Quaternionf();
        for (int s = 0; s < GAIT_SAMPLES; s++) {
            table.resetToRest(scratch);
            if (clip != null && clip.duration() > 0) clip.apply(clip.duration() * s / GAIT_SAMPLES, scratch);
            for (int b = 0; b < GAIT_BONES.length; b++) {
                int slot = table.slotOfName(GAIT_BONES[b]);
                if (slot < 0) { out[b][s * 4 + 3] = 1; continue; }
                int o = NodeRotation.offsetOf(table, slot);
                table.restRotation(slot, rest);
                q.set(scratch[o], scratch[o + 1], scratch[o + 2], scratch[o + 3]).normalize();
                var d = new Quaternionf(rest).invert().mul(q).normalize();
                if (d.w < 0) d.set(-d.x, -d.y, -d.z, -d.w);
                out[b][s * 4] = d.x; out[b][s * 4 + 1] = d.y; out[b][s * 4 + 2] = d.z; out[b][s * 4 + 3] = d.w;
            }
        }
        return out;
    }

    private static Quaternionf sample(float[] deltas, float phase, Quaternionf out) {
        float p = (phase - (float) Math.floor(phase)) * GAIT_SAMPLES;
        int a = (int) p % GAIT_SAMPLES, b = (a + 1) % GAIT_SAMPLES; float t = p - (int) p;
        var qa = new Quaternionf(deltas[a * 4], deltas[a * 4 + 1], deltas[a * 4 + 2], deltas[a * 4 + 3]);
        var qb = new Quaternionf(deltas[b * 4], deltas[b * 4 + 1], deltas[b * 4 + 2], deltas[b * 4 + 3]);
        return qa.nlerp(qb, t, out);
    }

    /** {@code q} raised to {@code s} (same axis, angle scaled), with the added angle capped at {@code max}. */
    static Quaternionf scaled(Quaternionf q, float s, float max, Quaternionf out) {
        float w = Math.clamp(q.w, -1, 1), angle = 2 * (float) Math.acos(Math.abs(w));
        float len = (float) Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z);
        if (len < 1e-6f || s <= 0) return out.identity();
        float sign = w < 0 ? -1 : 1, extra = Math.min(max, angle * s);
        return out.identity().fromAxisAngleRad(sign * q.x / len, sign * q.y / len, sign * q.z / len, extra);
    }

    /**
     * R94: deepen the stride. The authored community walk/run cycles bend the legs too little for Ori's low athletic
     * run, so the bend each leg bone already has at this phase is added again, scaled by gait speed (walk/run/sprint).
     */
    void gait(float phase, float walk, float run, float sprint) {
        float max = 0;
        var qw = new Quaternionf(); var qr = new Quaternionf(); var a = new Quaternionf(); var b = new Quaternionf();
        for (int i = 0; i < GAIT_BONES.length; i++) {
            float bone = FeelTuning.GAIT_BONE_GAIN[i / 2];
            if (walkDelta == null || walk + run <= .002f) { gaitQuat[i * 4] = gaitQuat[i * 4 + 1] = gaitQuat[i * 4 + 2] = 0; gaitQuat[i * 4 + 3] = 1; continue; }
            sample(walkDelta[i], phase, qw); sample(runDelta[i], phase, qr);
            scaled(qw, walk * FeelTuning.GAIT_GAIN_WALK * bone, FeelTuning.GAIT_EXTRA_MAX, a);
            scaled(qr, (run * FeelTuning.GAIT_GAIN_RUN + sprint * FeelTuning.GAIT_GAIN_SPRINT) * bone, FeelTuning.GAIT_EXTRA_MAX, b);
            a.mul(b).normalize();
            max = Math.max(max, 2 * (float) Math.acos(Math.min(1, Math.abs(a.w))));
            gaitQuat[i * 4] = a.x; gaitQuat[i * 4 + 1] = a.y; gaitQuat[i * 4 + 2] = a.z; gaitQuat[i * 4 + 3] = a.w;
        }
        gaitExtra = max;
    }

    /** Post-multiplies the bone's local rotation by a quaternion from {@link #gaitQuat} (same order as NodeRotation.compose). */
    private record QuatBone(int offset, int index, SpiritSecondary owner) implements PoseDriver {
        @Override public void apply(float parameter, float[] s) {
            float[] r = owner.gaitQuat;
            float rx = r[index], ry = r[index + 1], rz = r[index + 2], rw = r[index + 3];
            if (rw >= .99999f) return;
            float qx = s[offset], qy = s[offset + 1], qz = s[offset + 2], qw = s[offset + 3];
            s[offset] = qw * rx + qx * rw + qy * rz - qz * ry;
            s[offset + 1] = qw * ry - qx * rz + qy * rw + qz * rx;
            s[offset + 2] = qw * rz + qx * ry - qy * rx + qz * rw;
            s[offset + 3] = qw * rw - qx * rx - qy * ry - qz * rz;
        }
        @Override public float cycleSeconds() { return 0; }
    }

    private PoseDriver driver(NodeTable table, Matrix4f[] palette, String name, Vector3f modelAxis, int index) {
        int slot = table.slotOfName(name);
        var axis = new Vector3f(modelAxis);
        palette[slot].getUnnormalizedRotation(new Quaternionf()).invert().transform(axis);
        axis.normalize();
        return new Bone(NodeRotation.offsetOf(table, slot), axis.x, axis.y, axis.z, index, this);
    }

    private record Bone(int offset, float x, float y, float z, int index, SpiritSecondary owner) implements PoseDriver {
        @Override public void apply(float parameter, float[] scratch) {
            float a = owner.values[index];
            if (a != 0) NodeRotation.compose(scratch, offset, x, y, z, a);
        }
        @Override public float cycleSeconds() { return 0; }
    }

    /** Rest-pose slots after the chain angles: ear lift x3, ear spread x2, tail curl x5, feeler sweep x3. */
    private static final int REST = 13;
    /** R93: thigh_l/r + knee_l/r tuck angles (dynamic, written every publish). */
    private static final int LEG = 4;

    GltfAnimation clip() { return clip; }

    void reset() {
        for (Channel c : channels) c.chain.reset();
        lastVelocity = null; lastYaw = Float.NaN; smoothVelocity.zero(); smoothAccel.zero(); smoothTurn = 0;
        legTuck.reset(0); tuckOverride = Float.NaN;
        java.util.Arrays.fill(gaitQuat, 0); for (int i = 0; i < GAIT_BONES.length; i++) gaitQuat[i * 4 + 3] = 1;
        publish();
    }

    /** Landing (impact = downward speed in b/t) flops the ears and tail; take-off flicks them. */
    void land(float impact) {
        float k = FeelTuning.SEC_LAND_KICK * Math.min(1.5f, .35f + impact);
        earLiftL.chain.kick(-k); earLiftR.chain.kick(-k); tailLift.chain.kick(k * .6f);
        feelPitchL.chain.kick(k * .5f); feelPitchR.chain.kick(k * .5f);
        legTuck.kick(FeelTuning.LEG_LAND_KICK * Math.min(1.5f, .35f + impact));
    }

    /** R94: a weapon hit snaps the ears/feelers back and the tail out (lighter than a jump). */
    void flick(float strength) {
        float k = FeelTuning.SWING_EAR_FLICK * strength;
        earLiftL.chain.kick(-k); earLiftR.chain.kick(-k * .9f);
        earSwingL.chain.kick(k * .6f); earSwingR.chain.kick(k * .6f);
        tailYaw.chain.kick(-k * .8f); feelPitchL.chain.kick(-k * .5f); feelPitchR.chain.kick(-k * .5f);
    }

    private float tuckOverride = Float.NaN;
    /** R94: mantle: hold the knees tucked to this target (rad) while set; NaN returns control to the fall/land tuck. */
    void tuck(float target) { tuckOverride = target; }

    void jump() {
        float k = FeelTuning.SEC_JUMP_KICK;
        earLiftL.chain.kick(-k); earLiftR.chain.kick(-k); tailLift.chain.kick(-k * .8f);
        legTuck.kick(-FeelTuning.LEG_LAND_KICK * .35f);
    }

    /**
     * @param velocity world velocity (m/s) measured from interpolated positions
     * @param yawDegrees model facing (degrees, Minecraft yaw)
     * @param gaitPhase 0..1 stride phase; {@code gaitWeight} how much of the pose is running
     */
    void step(Vec3 velocity, float yawDegrees, float gaitPhase, float gaitWeight, boolean grounded, float dt) {
        if (!(dt > 0)) return;
        if (lastVelocity == null || !Float.isFinite(lastYaw)) { lastVelocity = velocity; lastYaw = yawDegrees; publish(); return; }
        // Body-local frame: Minecraft yaw 0 faces +Z; forward = (-sin, cos), right = (-cos, -sin).
        float yr = yawDegrees * Mth.DEG_TO_RAD, fx = -Mth.sin(yr), fz = Mth.cos(yr);
        float a = 1 - (float) Math.exp(-dt * 14);
        var v = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
        var previous = new Vector3f(smoothVelocity);
        smoothVelocity.lerp(v, a);
        var accel = new Vector3f(smoothVelocity).sub(previous).div(dt);
        smoothAccel.lerp(accel, 1 - (float) Math.exp(-dt * 20));
        float forwardSpeed = smoothVelocity.x * fx + smoothVelocity.z * fz;
        float forwardAccel = smoothAccel.x * fx + smoothAccel.z * fz;
        float rightAccel = smoothAccel.x * -fz + smoothAccel.z * fx;
        float turn = Mth.wrapDegrees(yawDegrees - lastYaw) * Mth.DEG_TO_RAD / dt;
        smoothTurn = Mth.lerp(1 - (float) Math.exp(-dt * 12), smoothTurn, turn);
        lastYaw = yawDegrees; lastVelocity = velocity;

        float max = FeelTuning.SEC_FORCE_MAX;
        float fa = Math.clamp(forwardAccel / FeelTuning.SEC_ACCEL_UNIT, -max, max);
        float ra = Math.clamp(rightAccel / FeelTuning.SEC_ACCEL_UNIT, -max, max);
        float va = Math.clamp(smoothAccel.y / FeelTuning.SEC_VERTICAL_ACCEL_UNIT, -max, max);
        float fall = Math.clamp(-smoothVelocity.y / FeelTuning.SEC_FALL_UNIT, -max, max);
        float drag = Math.clamp(Math.abs(forwardSpeed) / 6.4f, 0, 1.4f) * FeelTuning.SEC_DRAG;
        float tw = Math.clamp(smoothTurn / FeelTuning.SEC_TURN_UNIT, -max, max);
        float sway = FeelTuning.SEC_IDLE_SWAY * Math.max(0, 1 - Math.abs(forwardSpeed) / 3f);
        float rate = FeelTuning.SEC_IDLE_RATE;
        // Ears (pointing back and out): falling lifts the tips (air from below), speed streams them down/back, a
        // forward lurch throws them back, upward acceleration (take-off, landing) presses them down.
        float earLift = fall * .55f - drag * .6f - fa * .5f - va * .75f;
        earLiftL.chain.step(earLift, sway, rate, dt); earLiftR.chain.step(earLift, sway * .8f, rate * 1.13f, dt);
        // Turning and side acceleration swing both ears to the outside of the turn.
        float earSwing = -tw * .9f + ra * .6f;
        earSwingL.chain.step(earSwing, sway * .5f, rate * .7f, dt); earSwingR.chain.step(earSwing, sway * .5f, rate * .77f, dt);
        // Feelers (pointing up): pitch back with forward acceleration/speed, roll with side acceleration.
        float feelPitch = -fa * .8f - drag * .5f + va * .4f;
        feelPitchL.chain.step(feelPitch, sway * .7f, rate * 1.4f, dt); feelPitchR.chain.step(feelPitch, sway * .7f, rate * 1.5f, dt);
        float feelRoll = ra * .9f + tw * .5f;
        feelRollL.chain.step(feelRoll, sway * .4f, rate * 1.2f, dt); feelRollR.chain.step(feelRoll, sway * .4f, rate * 1.3f, dt);
        // Tail (pointing back): rises while falling, droops on take-off, swings opposite the turn and with the stride.
        float tailUp = fall * .45f - va * .55f + drag * .25f;
        tailLift.chain.step(tailUp, sway * .6f, rate * .8f, dt);
        float stride = grounded ? FeelTuning.SEC_TAIL_GAIT_SWAY * gaitWeight * Mth.sin(gaitPhase * Mth.TWO_PI) : 0;
        tailYaw.chain.step(-tw * 1.1f + ra * .7f + stride, sway * 1.4f, rate * .6f, dt);
        // R93: pull the knees in while falling, straighten a little while rising; grounded eases back to 0 (land kick handles impact).
        float legTarget = 0;
        if (!grounded) {
            if (smoothVelocity.y < 0)
                legTarget = Math.min(FeelTuning.LEG_TUCK_MAX, -smoothVelocity.y / FeelTuning.SEC_FALL_UNIT * FeelTuning.LEG_TUCK_FALL);
            else
                legTarget = -Math.min(FeelTuning.LEG_STRETCH_MAX, smoothVelocity.y / FeelTuning.SEC_FALL_UNIT * FeelTuning.LEG_STRETCH);
        }
        if (Float.isFinite(tuckOverride)) legTarget = tuckOverride;
        legTuck.step(legTarget, dt);
        lastForce = earLift;
        publish();
    }

    private void publish() {
        int index = 0;
        for (Channel c : channels) for (int i = 0; i < c.bones.length; i++) {
            if (index < values.length) values[index] = c.sign * c.chain.angle(i);
            index++;
        }
        if (values.length >= index + REST + LEG) {
            float tuck = legTuck.value, thigh = tuck * FeelTuning.LEG_THIGH_SHARE;
            // slots: thigh_l, thigh_r, knee_l, knee_r
            values[index + REST] = thigh;
            values[index + REST + 1] = thigh;
            values[index + REST + 2] = tuck;
            values[index + REST + 3] = tuck;
        }
    }

    Map<String, Object> diagnostics() {
        var out = new LinkedHashMap<String, Object>();
        out.put("ear_lift", earLiftL.chain.angles()); out.put("ear_swing", earSwingL.chain.angles());
        out.put("feeler_pitch", feelPitchL.chain.angles()); out.put("feeler_roll", feelRollL.chain.angles());
        out.put("tail_lift", tailLift.chain.angles()); out.put("tail_yaw", tailYaw.chain.angles());
        out.put("bones", values.length - REST - LEG); out.put("earForce", lastForce);
        out.put("legTuck", legTuck.value);
        out.put("gaitBones", gaitBones); out.put("gaitExtra", gaitExtra);
        return out;
    }
}
