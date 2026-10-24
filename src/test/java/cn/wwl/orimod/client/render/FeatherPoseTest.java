package cn.wwl.orimod.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeatherPoseTest {
    @Test void lowerQuillIsCentredAbovePawsAndVaneTrailsBehindAndUp() {
        for (int degrees = 0; degrees < 360; degrees += 45) {
            float yaw = (float)Math.toRadians(degrees);
            var a = new Vector3f(-.15f,1.35f,.12f).rotateY(yaw);
            var b = new Vector3f(.15f,1.35f,.12f).rotateY(yaw);
            var m = FeatherPose.matrix(a,b,new Vector3f(0,0,1).rotateY(yaw));
            assertNotNull(m);
            var centre = new Vector3f(a).add(b).mul(.5f);
            assertTrue(m.transformPosition(new Vector3f(0,.08f,0)).distance(centre)<.025f, "Lower shaft misses joined grip");
            var axis = m.transformDirection(new Vector3f(0,1,0)).normalize();
            assertTrue(axis.dot(new Vector3f(0,0,1).rotateY(yaw)) < -.9f, "Feather should trail backwards, not sideways");
            assertTrue(axis.y>.2f && axis.y<.45f, "Vane must rise slightly behind the head");
            float length=m.transformDirection(new Vector3f(0,1.353f,0)).length();
            assertTrue(length>=1.2f && length<=1.56f, "Feather should be 1–1.3 times Ori's 1.2-block height");
            assertTrue(m.transformDirection(new Vector3f(0,0,1)).normalize().y>.85f, "Feather stays upright, not catching the wind");
        }
    }
    @Test void missingOrDegeneratePoseIsHiddenInsteadOfInsideBody() {
        assertNull(FeatherPose.matrix(null,null,new Vector3f(0,0,1)));
        assertNull(FeatherPose.matrix(new Vector3f(),new Vector3f(0,0,.3f),new Vector3f(0,0,1)));
        assertNull(FeatherPose.matrix(new Vector3f(),new Vector3f(),new Vector3f(0,0,1)));
    }
    @Test void rollIsBoundedEvenDuringAChangingGlidePose() {
        var m = FeatherPose.matrix(new Vector3f(-.15f,1.2f,0),new Vector3f(.15f,1.8f,0),new Vector3f(0,0,1));
        assertNotNull(m);
        assertTrue(Math.abs(m.transformDirection(new Vector3f(1,0,0)).normalize().y)<.32f);
    }
}
