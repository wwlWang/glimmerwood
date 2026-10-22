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
    private final Vector3f tangent = new Vector3f(), center = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();

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
        tangent.set(start).mul(6*t2 - 6*t)
                .fma(3*t2 - 4*t + 1, startTangent)
                .fma(-6*t2 + 6*t, end).fma(3*t2 - 2*t, endTangent).normalize();
        rotation.rotationTo(direction, tangent);
        pose.pose().transformDirection(point.set(x-centerX,0,z-centerZ));
        rotation.transform(point).add(center);
        pose.normal().transform(normal);
        rotation.transform(normal).normalize();
    }
}
