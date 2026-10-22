package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SpiritShieldGripTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void blockingShieldStaysUprightInFrontAndItsHandleRemainsInEitherPalm(boolean left) {
        // Strongly tilted wrists, including the opposite hand; vanilla blocking adds 45/155 degrees again.
        var palm = new Quaternionf().rotationXYZ(.7f, left ? -.9f : .9f, left ? .5f : -.5f);
        var display = new Quaternionf().rotationXYZ((float)Math.toRadians(45), (float)Math.toRadians(left ? -155 : 155), 0);
        var handle = new Vector3f(-.5f, -.5f, -.625f); // centered built-in shield handle, after the Y/Z flip
        var displayMatrix = new Matrix4f().translate(left ? -.72f : -.22f, .68f, .13f).rotate(display);
        var contact = displayMatrix.transformPosition(new Vector3f(handle));
        var transform = new Matrix4f().translate(.1f, .7f, .3f).rotate(palm)
                .rotate(SpiritItemGrip.shieldRotation(palm, display)).scale(.72f)
                .translate(-contact.x, -contact.y, -contact.z).mul(displayMatrix);
        var at = transform.transformPosition(new Vector3f(handle));
        assertEquals(.1, at.x, 1e-6); assertEquals(.7, at.y, 1e-6); assertEquals(.3, at.z, 1e-6);
        // Decorated face +Z and top +Y in centered item coordinates (ShieldModel flips its own Y/Z).
        var normal = transform.transformDirection(new Vector3f(0, 0, 1)).normalize();
        var up = transform.transformDirection(new Vector3f(0, 1, 0)).normalize();
        assertTrue(normal.z > .999f && Math.abs(normal.y) < 1e-5, "Shield cannot become a horizontal table: " + normal);
        assertTrue(up.y > .999f, "Shield top must remain above the handle: " + up);
    }
}
