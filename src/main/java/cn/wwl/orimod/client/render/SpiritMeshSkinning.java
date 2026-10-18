package cn.wwl.orimod.client.render;

import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Small CPU view of the GPU pose, used only for outlines and stuck projectiles. */
final class SpiritMeshSkinning {
    private GemRenderGltfModel loaded;
    private Matrix4f[] palette;
    private final Vector3f weighted = new Vector3f();

    void prepare(SpiritPlayerVisual.Frame frame) {
        GemRenderGltfModel model = frame.model();
        if (loaded != model) { loaded = model; palette = model.newPalette(); }
        for (int i = 0; i < model.layout().nodeTable().nodeCount(); i++) frame.boneMatrix(i, palette[i]);
        for (var skin : model.layout().skins()) {
            for (int j = 0; j < skin.jointCount(); j++) {
                palette[skin.base() + j].set(palette[skin.jointSlots()[j]]).mul(skin.inverseBind()[j]);
            }
        }
    }

    void vertex(MeshGeometry geometry, int vertex, Vector3f position, Vector3f normal) {
        position.zero();
        if (normal != null) normal.zero();
        int joints = geometry.packedJoints(vertex);
        for (int influence = 0; influence < 4; influence++) {
            float weight = BoneAttributeCodec.decodeWeight(BoneAttributeCodec.packNormU8(geometry.weightChannel(vertex, influence)));
            if (weight == 0) continue;
            Matrix4f matrix = palette[BoneAttributeCodec.unpackJoint(joints, influence)];
            weighted.set(geometry.position(vertex, 0), geometry.position(vertex, 1), geometry.position(vertex, 2));
            matrix.transformPosition(weighted);
            position.fma(weight, weighted);
            if (normal != null) {
                weighted.set(geometry.normal(vertex, 0), geometry.normal(vertex, 1), geometry.normal(vertex, 2));
                matrix.transformDirection(weighted);
                normal.fma(weight, weighted);
            }
        }
        if (normal != null) {
            if (normal.lengthSquared() > 1e-8f) normal.normalize();
            else normal.set(0, 1, 0);
        }
    }
}
