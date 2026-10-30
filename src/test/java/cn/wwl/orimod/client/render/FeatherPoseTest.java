package cn.wwl.orimod.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class FeatherPoseTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void fixedSizeShaftContactsMatchAuthoredRaisedPawsAcrossYawAndMainHand(boolean leftMain) {
        float halfSpan=FeatherPose.ROOT.distance(FeatherPose.TIP)/2;
        for(int degrees=0;degrees<360;degrees+=45) {
            float yaw=(float)Math.toRadians(degrees);
            var right=new Vector3f(-halfSpan,1.055f,.04f).rotateY(yaw);
            var left=new Vector3f(halfSpan,1.055f,.04f).rotateY(yaw);
            var root=leftMain?left:right;var tip=leftMain?right:left;
            var m=FeatherPose.matrix(root,tip,new Vector3f(0,0,1).rotateY(yaw));
            assertNotNull(m);
            assertEquals(0,m.transformPosition(new Vector3f(FeatherPose.ROOT)).distance(root),1e-6);
            assertEquals(0,m.transformPosition(new Vector3f(FeatherPose.TIP)).distance(tip),1e-6);
            var axis=m.transformDirection(new Vector3f(0,1,0)).normalize();
            assertTrue(Math.abs(axis.dot(new Vector3f(0,0,1).rotateY(yaw)))<.1,"long axis crosses the head, not trails behind");
            assertTrue(m.transformDirection(new Vector3f(0,0,1)).normalize().y>.95,"vane lies nearly horizontal above head");
        }
    }
    @Test void missingOrDegeneratePoseIsHiddenInsteadOfInsideBody() {
        assertNull(FeatherPose.matrix(null,null,new Vector3f(0,0,1)));
        assertNull(FeatherPose.matrix(new Vector3f(),new Vector3f(0,0,.3f),new Vector3f(0,0,1)));
        assertNull(FeatherPose.matrix(new Vector3f(),new Vector3f(),new Vector3f(0,0,1)));
    }
    @Test void entryAndBankNeverShortenOrDistortTheOriginalFeather() {
        var root=new Vector3f(-.45f,1.055f,0);
        for(float span:new float[]{.1f,.3f,.51f,.9f,1.2f}) {
            var m=FeatherPose.matrix(root,new Vector3f(root).add(span,.1f,.02f),new Vector3f(0,0,1));
            assertNotNull(m);
            assertEquals(0,m.transformPosition(new Vector3f(FeatherPose.ROOT)).distance(root),1e-6);
            var x=m.transformDirection(new Vector3f(1,0,0));
            var y=m.transformDirection(new Vector3f(0,1,0));
            var z=m.transformDirection(new Vector3f(0,0,1));
            assertEquals(1,x.length(),1e-6);assertEquals(1,y.length(),1e-6);assertEquals(1,z.length(),1e-6);
            assertEquals(0,x.dot(y),1e-6);assertEquals(0,y.dot(z),1e-6);assertEquals(0,x.dot(z),1e-6);
            assertEquals(1,m.determinant3x3(),1e-6,"no reflection, shear, or anisotropic scale");
            assertEquals(1.35,m.transformDirection(new Vector3f(0,1.35f,0)).length(),1e-6);
        }
    }
}
