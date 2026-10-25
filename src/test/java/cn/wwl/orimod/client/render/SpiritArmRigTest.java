package cn.wwl.orimod.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritArmRigTest {
    @Test void vanillaCubeContactPreservesBothDisplayTransforms() {
        for(boolean left:new boolean[]{false,true}) {
            float side=left?-1:1;
            var matrix=new org.joml.Matrix4f().translation(side*.56f,-.52f,-.72f)
                    .rotateY((float)Math.toRadians(left?135:45)).scale(.4f);
            var local=SpiritFirstPersonGrip.blockSupport(matrix,left);
            var target=matrix.transformPosition(new Vector3f(local));
            var frame=SpiritArmRig.surface(left,target,SpiritArmRig.basis(new Vector3f(-side*.32f,.4f,-.86f).normalize()));
            assertEquals(0,target.distance(frame.palm()),.0001,"Cube must not drift toward the centre to meet the hand");
            assertEquals(side*.4129218f,frame.palm().x,.0001);
        }
    }
    @Test void mirroredBladeBasisPreservesFullPalmOrientation() {
        var display=new Quaternionf().rotationY((float)Math.toRadians(-90)).rotateZ((float)Math.toRadians(25));
        var attachment=new Quaternionf().rotationX((float)Math.toRadians(-70)).rotateY((float)Math.PI)
                .rotateY((float)Math.toRadians(-90)).rotateZ((float)Math.toRadians(55));
        var right=new Quaternionf(display).mul(new Quaternionf(attachment).invert());
        var leftDisplay=SpiritArmRig.mirror(display).rotateY((float)Math.PI);
        var leftAttachment=SpiritArmRig.mirror(attachment).rotateY((float)Math.PI);
        var left=new Quaternionf(leftDisplay).mul(leftAttachment.invert());
        assertEquals(1,Math.abs(left.dot(SpiritArmRig.mirror(right))),.00001);
    }
    @Test void itemAndPalmKeepContactUnderFullRotationIncludingShaderView() {
        var view=new org.joml.Matrix4f().rotateXYZ(.2f,-.5f,.1f).translate(.1f,.03f,-.2f);
        for(boolean left:new boolean[]{false,true}) {
            var old=new Vector3f(left?-.2f:.2f,-.06f,-.55f);var rotation=new Quaternionf().rotationXYZ(2,1,.6f);
            var frame=SpiritArmRig.solve(left,old,rotation);
            var correction=SpiritArmRig.correction(old,rotation,frame);
            assertEquals(0,correction.transformPosition(new Vector3f(old)).distance(frame.palm()),.00001);
            var input=new org.joml.Matrix4f(view).invert().translate(old).rotate(rotation);
            var output=new org.joml.Matrix4f(view).invert().mul(correction).mul(view).mul(input);
            assertEquals(0,new org.joml.Matrix4f(view).mul(output).getTranslation(new Vector3f()).distance(frame.palm()),.00001);
            var expected=new Vector3f(0,1,0).rotate(frame.rotation());
            var actual=new Vector3f(0,1,0).rotate(rotation);correction.transformDirection(actual);
            assertEquals(0,actual.distance(expected),.00001);
        }
    }
    @Test void fixedBonesAndElbowsOutsideViewAcrossUseRotations() {
        for(boolean left:new boolean[]{false,true})for(int pitch=-180;pitch<=180;pitch+=30)for(int yaw=-180;yaw<=180;yaw+=45) {
            var f=SpiritArmRig.solve(left,new Vector3f(left?-.2f:.2f,-.06f,-.55f),new Quaternionf().rotationXYZ((float)Math.toRadians(pitch),(float)Math.toRadians(yaw),.3f));
            assertEquals(SpiritArmRig.UPPER,f.shoulder().distance(f.elbow()),.0001);
            assertEquals(SpiritArmRig.FOREARM,f.wrist().distance(f.elbow()),.0001);
            assertTrue(f.elbow().y/(-f.elbow().z*.7002075f)<-1.08f);
            assertTrue(f.wristDegrees()<=SpiritArmRig.WRIST_LIMIT+.001f);
            assertTrue(Math.abs(f.twistDegrees())<=SpiritArmRig.TWIST_LIMIT+.001f);
            assertTrue(f.rotation().isFinite());
        }
    }
    @Test void leftRightBoneSolutionMirrorsWithoutFreeRoll() {
        var r=SpiritArmRig.solve(false,new Vector3f(.32f,-.34f,-.65f),new Quaternionf());
        var l=SpiritArmRig.solve(true,new Vector3f(-.32f,-.34f,-.65f),new Quaternionf());
        assertEquals(r.elbow().x,-l.elbow().x,.0001);assertEquals(r.elbow().y,l.elbow().y,.0001);assertEquals(r.elbow().z,l.elbow().z,.0001);
        assertEquals(r.wrist().x,-l.wrist().x,.0001);assertEquals(r.wrist().y,l.wrist().y,.0001);
    }
    @Test void arbitraryUseRollsKeepMeasuredJointLimits() {
        var random=new java.util.Random(42);
        for(int i=0;i<1024;i++) {
            var f=SpiritArmRig.solve(i%2==0,new Vector3f(random.nextFloat()*2-1,random.nextFloat()*2-1,-random.nextFloat()*2),
                    new Quaternionf().rotationXYZ(random.nextFloat()*6.28f,random.nextFloat()*6.28f,random.nextFloat()*6.28f));
            assertEquals(SpiritArmRig.UPPER,f.shoulder().distance(f.elbow()),.0001);
            assertEquals(SpiritArmRig.FOREARM,f.elbow().distance(f.wrist()),.0001);
            assertTrue(f.wristDegrees()<SpiritArmRig.WRIST_LIMIT+.1f,"Actual wrist="+f.wristDegrees());
            assertTrue(Math.abs(f.twistDegrees())<SpiritArmRig.TWIST_LIMIT+.1f,"Actual twist="+f.twistDegrees());
        }
    }
    @Test void extremeGripRequestsRemainReachableWithoutStretch() {
        for(boolean left:new boolean[]{false,true})for(float x:new float[]{-.9f,.2f,.9f})for(float y:new float[]{-.9f,-.2f,.9f})for(float z:new float[]{-1.5f,-.1f,.3f}) {
            var f=SpiritArmRig.solve(left,new Vector3f(x,y,z),new Quaternionf().rotationXYZ(1,2,3));
            assertEquals(SpiritArmRig.UPPER,f.shoulder().distance(f.elbow()),.0001);
            assertEquals(SpiritArmRig.FOREARM,f.elbow().distance(f.wrist()),.0001);
            assertTrue(f.palm().z<-.05f);
        }
    }
}
