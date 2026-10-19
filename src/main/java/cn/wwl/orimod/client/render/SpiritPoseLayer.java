package cn.wwl.orimod.client.render;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeRotation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;
import com.wf.gemrender.gltf.blend.BlendMask;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;

/**
 * R95: an authored pose written as per-bone angles about the model-space axes of the standing T rest pose (model faces +Z):
 * X = pitch (positive swings a hanging limb backward and tips the torso forward), Y = yaw, Z = roll (positive brings the
 * -X arm down from the T). See {@link SpiritPoses}. Each bone's angles compose as {@code rest * Ry * Rx * Rz} in that bone's own local axes, so the
 * world effect is "roll first, then pitch, then yaw" about the axes as they were at rest.
 * <p>
 * The driver writes an absolute local rotation (rest x offset), so the clip is used with {@code override} (replace the
 * masked bones, e.g. swim / glide / held-item cradle) or {@code additive} against the rest reference (adds the offset on
 * top of a playing clip, e.g. the run's chest lean and knee lift).
 * <p>
 * The axes are derived from rest world rotations composed here from the node table. (R92's {@code SpiritSecondary.driver}
 * reads the skinning palette, which is identity at rest, so its "model axes" are local axes; kept as tuned.)
 */
final class SpiritPoseLayer {
    /** Bones a pose may drive, in index order (constants below). */
    static final String[] BONES = {"spine", "neck", "head", "upper_arm_l", "upper_arm_r", "forearm_l", "forearm_r", "hand_l", "hand_r",
            "thigh_l", "thigh_r", "knee_l", "knee_r", "ankle_l", "ankle_r", "abdomen", "tail_1", "ear_l_1", "ear_r_1", "antenna_l_1", "antenna_r_1"};
    static final int SPINE = 0, NECK = 1, HEAD = 2, UPPER_ARM_L = 3, UPPER_ARM_R = 4, FOREARM_L = 5, FOREARM_R = 6, HAND_L = 7, HAND_R = 8,
            THIGH_L = 9, THIGH_R = 10, KNEE_L = 11, KNEE_R = 12, ANKLE_L = 13, ANKLE_R = 14, ABDOMEN = 15,
            TAIL = 16, EAR_L = 17, EAR_R = 18, FEELER_L = 19, FEELER_R = 20;

    private final String name;
    /** Model-space angles (x, y, z) per bone, accumulated by {@link #add} each frame. */
    final float[] angles = new float[BONES.length * 3];
    private final float[] local = new float[BONES.length * 4];
    private final float[] axes = new float[BONES.length * 9];
    private final float[] rest = new float[BONES.length * 4];
    private final boolean[] present = new boolean[BONES.length];
    private GltfAnimation clip;
    private NodeTable table;

    SpiritPoseLayer(String name) { this.name = name; }

    void bind(NodeTable table) {
        this.table = table;
        float[] state = table.newScratch();
        table.resetToRest(state);
        var world = new Matrix4f[table.nodeCount()];
        var m = new Matrix4f();
        for (int slot : table.evaluationOrder()) {
            int parent = table.parentSlots()[slot];
            table.localTransform(state, slot, m);
            world[slot] = parent < 0 ? new Matrix4f(m) : new Matrix4f(world[parent]).mul(m);
        }
        var drivers = new ArrayList<PoseDriver>();
        var q = new Quaternionf(); var r = new Quaternionf();
        for (int i = 0; i < BONES.length; i++) {
            int slot = table.slotOfName(BONES[i]);
            present[i] = slot >= 0;
            if (slot < 0) continue;
            restRotation(world[slot], q);
            q.invert();
            Vector3f[] model = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
            for (int a = 0; a < 3; a++) {
                var v = q.transform(model[a]).normalize();
                axes[i * 9 + a * 3] = v.x; axes[i * 9 + a * 3 + 1] = v.y; axes[i * 9 + a * 3 + 2] = v.z;
            }
            table.restRotation(slot, r);
            rest[i * 4] = r.x; rest[i * 4 + 1] = r.y; rest[i * 4 + 2] = r.z; rest[i * 4 + 3] = r.w;
            drivers.add(new Bone(NodeRotation.offsetOf(table, slot), i, this));
        }
        clip = GltfAnimation.procedural(name, drivers.toArray(PoseDriver[]::new));
        clear(); publish();
    }

    GltfAnimation clip() { return clip; }

    /** Mask of the given bone indices (present ones only). */
    BlendMask mask(int... bones) {
        var names = new ArrayList<String>();
        for (int b : bones) if (present[b]) names.add(BONES[b]);
        return BlendMask.nodes(table, names.toArray(String[]::new));
    }

    void clear() { java.util.Arrays.fill(angles, 0); }

    void add(int bone, float x, float y, float z, float weight) {
        angles[bone * 3] += x * weight; angles[bone * 3 + 1] += y * weight; angles[bone * 3 + 2] += z * weight;
    }

    /** Mirror of {@link #add} for the +X side: pitch stays, yaw and roll flip. */
    void addMirrored(int bone, float x, float y, float z, float weight) { add(bone, x, -y, -z, weight); }

    /** Rotation part of a scaled world matrix. The glb chain carries 0.16 x 0.01 x 51 scale; JOML's
     *  getNormalizedRotation gives a non-unit, wrong quaternion there, so normalize the columns first. */
    static Quaternionf restRotation(Matrix4f world, Quaternionf out) {
        var r = new org.joml.Matrix3f(); world.get3x3(r);
        var c = new Vector3f();
        for (int i = 0; i < 3; i++) { r.getColumn(i, c); c.normalize(); r.setColumn(i, c); }
        return out.setFromNormalized(r).normalize();
    }

    Quaternionf target(int bone) { return new Quaternionf(local[bone * 4], local[bone * 4 + 1], local[bone * 4 + 2], local[bone * 4 + 3]); }

    float angle(int bone, int axis) { return angles[bone * 3 + axis]; }

    /** Turns the accumulated angles into absolute local rotations for the drivers. */
    void publish() {
        var q = new Quaternionf(); var t = new Quaternionf();
        for (int i = 0; i < BONES.length; i++) {
            q.set(rest[i * 4], rest[i * 4 + 1], rest[i * 4 + 2], rest[i * 4 + 3]);
            if (!present[i]) { q.identity(); }
            // rest * Ry * Rx * Rz (local axes): world effect Rz first, then Rx, then Ry.
            float ax = angles[i * 3], ay = angles[i * 3 + 1], az = angles[i * 3 + 2];
            if (ay != 0) q.mul(t.fromAxisAngleRad(axes[i * 9 + 3], axes[i * 9 + 4], axes[i * 9 + 5], ay));
            if (ax != 0) q.mul(t.fromAxisAngleRad(axes[i * 9], axes[i * 9 + 1], axes[i * 9 + 2], ax));
            if (az != 0) q.mul(t.fromAxisAngleRad(axes[i * 9 + 6], axes[i * 9 + 7], axes[i * 9 + 8], az));
            q.normalize();
            local[i * 4] = q.x; local[i * 4 + 1] = q.y; local[i * 4 + 2] = q.z; local[i * 4 + 3] = q.w;
        }
    }

    private record Bone(int offset, int index, SpiritPoseLayer owner) implements PoseDriver {
        @Override public void apply(float parameter, float[] s) {
            float[] l = owner.local;
            s[offset] = l[index * 4]; s[offset + 1] = l[index * 4 + 1]; s[offset + 2] = l[index * 4 + 2]; s[offset + 3] = l[index * 4 + 3];
        }
        @Override public float cycleSeconds() { return 0; }
    }
}
