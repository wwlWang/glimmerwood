package cn.wwl.orimod.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeatherPoseTest {
    @Test void bothPawsMeetTheLowerQuillAndTheSailFacesUp() {
        for (int degrees = 0; degrees < 360; degrees += 45) {
            float yaw = (float)Math.toRadians(degrees);
            var a = new Vector3f(-.15f,1.35f,.12f).rotateY(yaw);
            var b = new Vector3f(.15f,1.35f,.12f).rotateY(yaw);
            var m = FeatherPose.matrix(a,b,new Vector3f(0,0,1).rotateY(yaw));
            assertNotNull(m);
            assertTrue(m.transformPosition(new Vector3f(0,.02f,0)).distance(a)<.025f, "First paw misses quill");
            assertTrue(m.transformPosition(new Vector3f(0,.25f,0)).distance(b)<.025f, "Second paw misses quill");
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
        assertTrue(Math.abs(m.transformDirection(new Vector3f(0,1,0)).normalize().y)<.32f);
    }
}
