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
    static MeshGeometry ring() {
        return MeshGeometry.of(new float[]{-.025f,-.65f,-.025f,.025f,-.65f,.025f},
                new float[]{0,0,1,0,0,1},new float[]{0,0,1,1},new int[]{0,1,0},com.wf.gemrender.gltf.skin.VertexSkinning.rigid(2,0),SpriteUv.IDENTITY,0);
    }
    @Test void raisedGripDoesNotStretchForearmIntoADiagonalRod() {
        var pose=new PoseStack();pose.translate(.20,-.06,-.55);pose.scale(1.25f,1.25f,1.25f);
        var arm=new SpiritForearm(pose.last(),ring(),false);
        var previous=new Vector3f();float length=0;
        for(int i=0;i<=100;i++) {
            var point=new Vector3f();arm.vertex(0,-.09f-.56f*SpiritForearm.ELBOW_T*i/100,0,point,new Vector3f(0,0,1));
            if(i>0)length+=point.distance(previous);previous.set(point);
        }
        assertTrue(length<=.445f,"Forearm length="+length+"; must be a fixed bone, not stretched to screen corner");
    }
    @Test void proximalForearmEndsBelowVisibleScreenForBothArms() {
        for(boolean left:new boolean[]{false,true}) {
            var pose=new PoseStack();pose.translate(left?-.2:.2,-.06,-.55);
            var arm=new SpiritForearm(pose.last(),ring(),left);var end=new Vector3f();
            arm.vertex(0,-.65f,0,end,new Vector3f(0,0,1));
            float screenY=end.y/(-end.z*(float)Math.tan(Math.toRadians(35)));
            assertTrue(screenY < -1.08f,"Elbow visible at NDC y="+screenY+"; shoulder/proximal arm must stay outside view");
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"right_open","left_open","right_grip","left_grip"})
    void actualPawVerticesRemainRigidUnderVanillaUseStretch(String name) throws Exception {
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
        var forearm=new SpiritForearm(pose.last(),mesh,name.startsWith("left"));
        Vector3f first=null,firstSource=null;int checked=0;
        for(int i=0;i<n;i++) {
            var source=new Vector3f(positions[i*3],positions[i*3+1],positions[i*3+2]);
            if(source.y<-.09001f)continue;
            var point=new Vector3f();forearm.vertex(source.x,source.y,source.z,point,new Vector3f(0,0,1));
            if(first==null) { first=point;firstSource=source; }
            assertEquals(source.distance(firstSource)*SpiritArmRig.PAW_SCALE,point.distance(first),1e-5,
                    "paw vertex "+i+" must not be stretched by item use or forearm IK");
            checked++;
        }
        assertTrue(checked>100,"check the actual palm/finger mesh, not just the forearm rings");
    }
}
