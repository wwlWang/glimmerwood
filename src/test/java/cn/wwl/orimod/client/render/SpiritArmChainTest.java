package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.texture.SpriteUv;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritArmChainTest {
    @org.junit.jupiter.api.BeforeAll static void renderThread() {
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())
            com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"right_open","left_open","right_grip","left_grip"})
    void actualPawUsesTheWholeVanillaMatrixWithoutAnyVertexReposing(String name) throws Exception {
        byte[] bytes;
        try(var in=getClass().getResourceAsStream("/assets/orimod/models/entity/paw_"+name+".glb")) {
            bytes=java.util.Objects.requireNonNull(in).readAllBytes();
        }
        var data=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int jsonLength=data.getInt(12), binaryStart=20+jsonLength+8;
        var gltf=com.google.gson.JsonParser.parseString(new String(bytes,20,jsonLength,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var attributes=gltf.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0)
                .getAsJsonObject().getAsJsonObject("attributes");
        assertFalse(attributes.has("JOINTS_0"),"first-person fingers are baked, not separately skinned");
        var accessor=gltf.getAsJsonArray("accessors").get(attributes.get("POSITION").getAsInt()).getAsJsonObject();
        var view=gltf.getAsJsonArray("bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int offset=binaryStart+(view.has("byteOffset")?view.get("byteOffset").getAsInt():0)
                +(accessor.has("byteOffset")?accessor.get("byteOffset").getAsInt():0);
        int stride=view.has("byteStride")?view.get("byteStride").getAsInt():12,n=accessor.get("count").getAsInt();
        float[] positions=new float[n*3],normals=new float[n*3];
        for(int i=0;i<n;i++) { for(int axis=0;axis<3;axis++)positions[i*3+axis]=data.getFloat(offset+i*stride+axis*4); normals[i*3+2]=1; }
        var mesh=MeshGeometry.of(positions,normals,new float[n*2],new int[]{0,1,2},
                com.wf.gemrender.gltf.skin.VertexSkinning.rigid(n,0),SpriteUv.IDENTITY,0);
        var pose=new PoseStack();pose.translate(.56,-.32,-.72);pose.scale(1,1,1.2f);
        pose.mulPose(new org.joml.Matrix4f().rotateXYZ(.7f,-1.1f,.3f));
        var itemMatrix=new org.joml.Matrix4f(pose.last().pose());
        var contact=new Vector3f(.2f,.1f,0);var rotation=new org.joml.Quaternionf().rotationXYZ(.2f,.3f,.4f);
        SpiritPawGeometry.place(pose,contact,rotation);
        var expected=new org.joml.Matrix4f(itemMatrix).translate(contact).rotate(rotation).scale(SpiritPawGeometry.SCALE);
        int checked=0;
        for(int i=0;i<n;i++) {
            var source=new Vector3f(positions[i*3],positions[i*3+1],positions[i*3+2]);
            if(source.y<SpiritPawGeometry.WRIST)continue;
            var point=pose.last().pose().transformPosition(new Vector3f(source));
            assertEquals(0,point.distance(expected.transformPosition(new Vector3f(source))),1e-6,
                    "paw vertex "+i+" must follow the exact item matrix, including its full swing/use displacement");
            checked++;
        }
        assertTrue(checked>100,"check actual palm/finger vertices");
        // The rendered triangle filter excludes every generated sleeve vertex, independently of pose.
        var testMesh=MeshGeometry.of(new float[]{0,-.09f,0, .01f,0,0, -.01f,0,0, 0,-.65f,0},
                new float[12],new float[8],new int[]{0,1,2,0,3,1},com.wf.gemrender.gltf.skin.VertexSkinning.rigid(4,0),SpriteUv.IDENTITY,0);
        assertTrue(SpiritPawGeometry.pawTriangle(testMesh,0));
        assertFalse(SpiritPawGeometry.pawTriangle(testMesh,3),"The removed arm cannot reappear as a stretched sleeve");
    }
}
