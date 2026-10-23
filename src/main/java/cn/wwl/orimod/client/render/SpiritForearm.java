package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.MeshGeometry;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Curves from the gripping palm toward a visible forearm at the lower corner of the view. */
final class SpiritForearm {
    private final PoseStack.Pose pose;
    private final float centerX, centerZ;
    private final Vector3f start, end, direction, startTangent, endTangent;
    private final Vector3f center = new Vector3f();
    /** Parallel-transported ring rotations along the curve. A direct start-to-tangent rotation is undefined when the
     * curve turns back past 90 degrees (eating, raised tools), and its free roll made the forearm spin from frame to frame. */
    private final Quaternionf[] rotations = new Quaternionf[SAMPLES + 1];
    private static final int SAMPLES = 40;

    SpiritForearm(PoseStack.Pose pose, MeshGeometry mesh, boolean left) {
        this.pose = pose;
        float minX = Float.POSITIVE_INFINITY, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxZ = maxX;
        for (int i = 0; i < mesh.vertexCount(); i++) {
            if (mesh.position(i, 1) > -.649f) continue;
            minX = Math.min(minX, mesh.position(i,0)); maxX = Math.max(maxX, mesh.position(i,0));
            minZ = Math.min(minZ, mesh.position(i,2)); maxZ = Math.max(maxZ, mesh.position(i,2));
        }
        centerX = (minX + maxX) * .5f; centerZ = (minZ + maxZ) * .5f;
        start = pose.pose().transformPosition(new Vector3f(centerX, -.09f, centerZ));
        end = SpiritHandSpace.point(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),
                new Vector3f(left ? -.62f : .62f, -.62f, -.85f));
        direction = pose.pose().transformDirection(new Vector3f(0,-1,0)).normalize();
        float length = start.distance(end);
        startTangent = new Vector3f(direction).mul(length * .55f);
        endTangent = new Vector3f(end).sub(start).mul(.65f);
        var previous = new Vector3f(direction);
        rotations[0] = new Quaternionf();
        for (int i = 1; i <= SAMPLES; i++) {
            var tangent = tangent(i / (float) SAMPLES, new Vector3f());
            rotations[i] = new Quaternionf().rotationTo(previous, tangent).mul(rotations[i - 1]).normalize();
            previous.set(tangent);
        }
    }

    private Vector3f tangent(float t, Vector3f out) {
        float t2 = t*t;
        out.set(start).mul(6*t2 - 6*t)
                .fma(3*t2 - 4*t + 1, startTangent)
                .fma(-6*t2 + 6*t, end).fma(3*t2 - 2*t, endTangent);
        // Hermite tangents vanish only for degenerate (zero-length) arms; keep the wrist direction then.
        return out.lengthSquared() < 1e-12f ? out.set(direction) : out.normalize();
    }

    void vertex(float x, float y, float z, Vector3f point, Vector3f normal) {
        if (y >= -.09001f) {
            pose.pose().transformPosition(point.set(x,y,z));
            pose.normal().transform(normal).normalize();
            return;
        }
        float t = Math.clamp((-y - .09f) / .56f, 0, 1), t2 = t*t, t3 = t2*t;
        center.set(start).mul(2*t3 - 3*t2 + 1)
                .fma(t3 - 2*t2 + t, startTangent)
                .fma(-2*t3 + 3*t2, end).fma(t3 - t2, endTangent);
        var rotation = rotations[Math.round(t * SAMPLES)];
        pose.pose().transformDirection(point.set(x-centerX,0,z-centerZ));
        rotation.transform(point).add(center);
        pose.normal().transform(normal);
        rotation.transform(normal).normalize();
    }
}
