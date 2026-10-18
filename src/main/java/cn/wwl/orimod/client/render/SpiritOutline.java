package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.GltfMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The native outline target needs vertices; Flywheel's instanced body does not write that target. */
public final class SpiritOutline {
    private static final SpiritMeshSkinning SKIN = new SpiritMeshSkinning();
    private static float[] vertices = new float[0];
    private static final Vector3f POSITION = new Vector3f();

    private SpiritOutline() { }

    public static void render(Player player, SpiritPlayerVisual.Frame frame, PoseStack initial, MultiBufferSource buffers) {
        if (!Minecraft.getInstance().shouldEntityAppearGlowing(player)) return;
        var model = frame.model();
        SKIN.prepare(frame);
        Matrix4f transform = SpiritVanillaLayers.base(initial, player, frame).last().pose();
        for (var configured : model.model().meshes()) {
            if (!(configured.mesh() instanceof GltfMesh mesh)) continue;
            var geometry = mesh.geometry();
            if (vertices.length < geometry.vertexCount() * 3) vertices = new float[geometry.vertexCount() * 3];
            for (int vertex = 0; vertex < geometry.vertexCount(); vertex++) {
                SKIN.vertex(geometry, vertex, POSITION, null);
                vertices[vertex * 3] = POSITION.x; vertices[vertex * 3 + 1] = POSITION.y; vertices[vertex * 3 + 2] = POSITION.z;
            }
            var consumer = buffers.getBuffer(RenderType.outline(configured.material().texture()));
            // RenderType.outline uses quads. Repeating the last corner preserves each glTF triangle.
            for (int index = 0; index < geometry.indexCount(); index += 3) {
                for (int corner = 0; corner < 4; corner++) {
                    int vertex = geometry.index(index + Math.min(corner, 2));
                    consumer.addVertex(transform, vertices[vertex * 3], vertices[vertex * 3 + 1], vertices[vertex * 3 + 2])
                            .setColor(-1).setUv(geometry.texCoord(vertex, 0), geometry.texCoord(vertex, 1));
                }
            }
        }
    }
}
