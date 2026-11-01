package cn.wwl.orimod.client.render;

import com.wf.gemrender.gltf.MeshGeometry;

/** The baked paw ends at its wrist ring. Held weapons never draw the generated sleeve. */
final class SpiritPawGeometry {
    static final float SCALE = 1.25f;
    static void place(com.mojang.blaze3d.vertex.PoseStack stack, org.joml.Vector3f point, org.joml.Quaternionf rotation) {
        stack.translate(point.x, point.y, point.z);
        stack.mulPose(rotation);
        stack.scale(SCALE, SCALE, SCALE);
    }
    static final float WRIST = -.09001f;
    private SpiritPawGeometry() { }
    static boolean pawTriangle(MeshGeometry mesh, int triangle) {
        for (int corner = 0; corner < 3; corner++)
            if (mesh.position(mesh.index(triangle + corner), 1) < WRIST) return false;
        return true;
    }
}
