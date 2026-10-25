package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.gltf.MeshGeometry;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Textured rings on a fixed-length forearm; the upper arm is outside the camera. */
final class SpiritForearm {
    private final Matrix4f inverseView;
    private final SpiritArmRig.Frame frame;
    private final float centerX,centerZ;
    private final Quaternionf forearmRotation;
    SpiritForearm(PoseStack.Pose pose,MeshGeometry mesh,boolean left) {
        this(SpiritArmRig.solve(left,new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose()).getTranslation(new Vector3f()),
                new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose()).getUnnormalizedRotation(new Quaternionf())),mesh);
    }
    SpiritForearm(SpiritArmRig.Frame frame,MeshGeometry mesh) {
        this.frame=frame;inverseView=new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        float minX=Float.POSITIVE_INFINITY,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxZ=maxX;
        for(int i=0;i<mesh.vertexCount();i++) {
            if(mesh.position(i,1)>-.649f)continue;
            minX=Math.min(minX,mesh.position(i,0));maxX=Math.max(maxX,mesh.position(i,0));
            minZ=Math.min(minZ,mesh.position(i,2));maxZ=Math.max(maxZ,mesh.position(i,2));
        }
        centerX=Float.isFinite(minX)?(minX+maxX)*.5f:0;centerZ=Float.isFinite(minZ)?(minZ+maxZ)*.5f:0;
        forearmRotation=SpiritArmRig.basis(new Vector3f(frame.wrist()).sub(frame.elbow()).normalize());
    }
    void vertex(float x,float y,float z,Vector3f point,Vector3f normal) {
        if(y>=-.09001f) {
            point.set(x,y,z).mul(SpiritArmRig.PAW_SCALE).rotate(frame.rotation()).add(frame.palm());normal.rotate(frame.rotation());
        } else {
            float t=Math.clamp((-y-.09f)/.56f,0,1);
            var rotation=new Quaternionf(frame.rotation()).slerp(forearmRotation,Math.min(1,t*4));
            point.set(x-centerX,0,z-centerZ).mul(SpiritArmRig.PAW_SCALE).rotate(rotation)
                    .add(new Vector3f(frame.wrist()).lerp(frame.elbow(),t));normal.rotate(rotation);
        }
        inverseView.transformPosition(point);inverseView.transformDirection(normal).normalize();
    }
}
