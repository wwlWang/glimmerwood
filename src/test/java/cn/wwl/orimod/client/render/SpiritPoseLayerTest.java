package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritPoseLayerTest {
    /** R95 root cause: the glb chain (Sketchfab 0.159 x fbx 0.01 x Armature 51.3) scales every world matrix by ~0.08;
     *  the rest rotation used for the pose axes must still be the exact unit rotation. */
    @Test void restRotationIgnoresChainScale() {
        var expected = new Quaternionf().rotateX(-1.4f).rotateZ(1.6f).rotateY(.3f);
        var world = new Matrix4f().translate(.1f, 2, 3).rotate(expected).scale(.0814f);
        var q = SpiritPoseLayer.restRotation(world, new Quaternionf());
        assertEquals(1, q.lengthSquared(), 1e-5);
        assertTrue(Math.abs(q.dot(expected)) > 1 - 1e-5, "got " + q + " want " + expected);
    }
}
