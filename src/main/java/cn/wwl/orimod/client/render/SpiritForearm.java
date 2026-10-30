package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.gltf.MeshGeometry;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The baked sleeve rings cover a fixed-length forearm and upper arm; the paw stays rigid. */
final class SpiritForearm {
    static final float ELBOW_T = .6f; // An existing ring, so no triangle spans across the elbow.
    private final Matrix4f inverseView;
    private final SpiritArmRig.Frame frame;
    private final float centerX,centerZ;
    private final Quaternionf forearmRotation, upperRotation;
    SpiritForearm(PoseStack.Pose pose,MeshGeometry mesh,boolean left) {
        inverseView=new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        var inView=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        float minX=Float.POSITIVE_INFINITY,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxZ=maxX;
        for(int i=0;i<mesh.vertexCount();i++) {
            if(mesh.position(i,1)>-.649f)continue;
            minX=Math.min(minX,mesh.position(i,0));maxX=Math.max(maxX,mesh.position(i,0));
            minZ=Math.min(minZ,mesh.position(i,2));maxZ=Math.max(maxZ,mesh.position(i,2));
        }
        centerX=Float.isFinite(minX)?(minX+maxX)*.5f:0;centerZ=Float.isFinite(minZ)?(minZ+maxZ)*.5f:0;
        frame=SpiritArmRig.follow(left,inView.getTranslation(new Vector3f()),inView.getUnnormalizedRotation(new Quaternionf()),
                new Vector3f(centerX,-.09f,centerZ));
        // Transport the palm's roll along the arm. Rebuilding a screen-facing basis
        // introduced up to 180 degrees of twist in the first ring and pinched the wrist.
        forearmRotation=along(frame.rotation(),new Vector3f(frame.wrist()).sub(frame.elbow()));
        upperRotation=along(forearmRotation,new Vector3f(frame.elbow()).sub(frame.shoulder()));
    }
    private static Quaternionf along(Quaternionf from,Vector3f distal) {
        return new Quaternionf().rotationTo(from.transform(new Vector3f(0,1,0)),distal.normalize()).mul(from);
    }
    void vertex(float x,float y,float z,Vector3f point,Vector3f normal) {
        if(y>=-.09001f) {
            point.set(x,y,z).mul(SpiritArmRig.PAW_SCALE).rotate(frame.rotation()).add(frame.palm());normal.rotate(frame.rotation());
        } else {
            float t=Math.clamp((-y-.09f)/.56f,0,1);
            boolean lower=t<=ELBOW_T;
            float segment=lower?t/ELBOW_T:(t-ELBOW_T)/(1-ELBOW_T);
            var rotation=lower?new Quaternionf(frame.rotation()).slerp(forearmRotation,segment)
                    :new Quaternionf(forearmRotation).slerp(upperRotation,segment);
            point.set(x-centerX,0,z-centerZ).mul(SpiritArmRig.PAW_SCALE).rotate(rotation)
                    .add(lower?new Vector3f(frame.wrist()).lerp(frame.elbow(),segment)
                            :new Vector3f(frame.elbow()).lerp(frame.shoulder(),segment));normal.rotate(rotation);
        }
        inverseView.transformPosition(point);inverseView.transformDirection(normal).normalize();
    }
}
