package cn.wwl.orimod.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoftGlowClipTest {
    @Test void flashSizedBillboardIsTransparentAtGroundAndKeepsTextureCoordinates() {
        var vertices = SoftGlowClip.vertices(new Vector3f(0, .6f, 0), new Vector3f(7, 0, 0),
                new Vector3f(0, 7, 0), 0, .45f);
        assertFalse(vertices.isEmpty());
        assertEquals(0, vertices.size() % 4);
        assertTrue(vertices.stream().anyMatch(v -> v.y() == 0 && v.alpha() == 0));
        assertTrue(vertices.stream().anyMatch(v -> v.alpha() == 1));
        for (var v : vertices) {
            assertTrue(v.y() >= 0);
            assertEquals((v.x() / 7 + 1) / 2, v.u(), 1e-6);
            assertEquals((1 - (v.y() - .6f) / 7) / 2, v.v(), 1e-6);
            assertTrue(v.alpha() >= 0 && v.alpha() <= 1);
        }
    }

    @Test void tiltedAndRolledCameraStillClipsExactlyAtWorldFloor() {
        var camera = new Quaternionf().rotateXYZ(.4f, .8f, .2f);
        var vertices = SoftGlowClip.vertices(new Vector3f(2, -1, 3), camera.transform(new Vector3f(7, 0, 0)),
                camera.transform(new Vector3f(0, 7, 0)), -1.6f, .45f);
        assertFalse(vertices.isEmpty());
        for (var v : vertices) {
            assertTrue(v.y() >= -1.6f);
            if (v.y() == -1.6f) assertEquals(0, v.alpha());
            assertTrue(v.u() >= 0 && v.u() <= 1 && v.v() >= 0 && v.v() <= 1);
        }
    }

    @Test void fullyBuriedBillboardEmitsNothing() {
        assertTrue(SoftGlowClip.vertices(new Vector3f(0, -2, 0), new Vector3f(1, 0, 0),
                new Vector3f(0, 1, 0), 0, .45f).isEmpty());
    }

    @Test void billboardAboveFadeBandRetainsFullAlpha() {
        var vertices = SoftGlowClip.vertices(new Vector3f(0, 2, 0), new Vector3f(1, 0, 0),
                new Vector3f(0, 1, 0), 0, .45f);
        assertFalse(vertices.isEmpty());
        assertTrue(vertices.stream().allMatch(v -> v.alpha() == 1));
    }
}
