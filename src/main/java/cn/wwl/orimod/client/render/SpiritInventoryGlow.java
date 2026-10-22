package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.GltfMesh;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** DirectRenderer's GUI shader omits the material's emissive band. Supply it from the same posed mesh. */
final class SpiritInventoryGlow {
    private static final SpiritMeshSkinning SKIN = new SpiritMeshSkinning();
    private static float[] positions = new float[0];
    private SpiritInventoryGlow() { }

    static void render(SpiritPlayerVisual.Frame frame, Matrix4f pose, MultiBufferSource buffers) {
        SKIN.prepare(frame);
        var p = new Vector3f();
        for (var part : frame.model().model().meshes()) {
            if (!(part.mesh() instanceof GltfMesh mesh)) continue;
            var geometry = mesh.geometry();
            if (positions.length < geometry.vertexCount() * 3) positions = new float[geometry.vertexCount() * 3];
            for (int i = 0; i < geometry.vertexCount(); i++) {
                SKIN.vertex(geometry, i, p, null); pose.transformPosition(p);
                positions[i*3]=p.x; positions[i*3+1]=p.y; positions[i*3+2]=p.z;
            }
            var out = buffers.getBuffer(SpiritHandRenderType.emission(part.material().texture()));
            for (int index = 0; index < geometry.indexCount(); index++) {
                int i = geometry.index(index);
                out.addVertex(positions[i*3], positions[i*3+1], positions[i*3+2])
                        .setColor(1f, 1f, 1f, 1f)
                        .setUv(geometry.texCoord(i,0), geometry.texCoord(i,1) + SpiritSelfLight.EMISSION_UV_OFFSET)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0,1,0);
            }
        }
        float scale = pose.getScale(new Vector3f()).x;
        var screen = new PoseStack().last();
        for (String bone : new String[]{"spine", "head", "hand_l", "hand_r", "ear_l_3", "ear_r_3"}) {
            int slot = frame.model().layout().nodeTable().slotOfName(bone);
            if (slot < 0) continue;
            var matrix = frame.boneMatrix(slot, new Matrix4f());
            pose.transformPosition(p.set(matrix.m30(), matrix.m31(), matrix.m32()));
            float radius = scale * (bone.equals("spine") ? .75f : .26f);
            SpiritEnergy.quad(buffers.getBuffer(SpiritEnergy.glow()), screen, p,
                    new Vector3f(radius,0,0), new Vector3f(0,radius,0), 0xD9F0FF, .40f);
        }
    }
}
