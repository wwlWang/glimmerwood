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
            var point=new Vector3f();arm.vertex(0,-.09f-.56f*i/100,0,point,new Vector3f(0,0,1));
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
}
