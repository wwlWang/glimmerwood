package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritHandSpaceTest {
    @Test void forearmEndpointAndHeldOffsetStayInViewSpaceForVanillaAndIris() {
        for (float yaw : new float[]{0, 1.3f, 3.14f}) for (float pitch : new float[]{-.8f, 0, .26f, .8f}) {
            var view = new Matrix4f().rotateX(pitch).rotateY(yaw);
            for (float side : new float[]{-1, 1}) {
                var end = new Vector3f(side * .62f, -.62f, -.85f);
                assertTrue(view.transformPosition(SpiritHandSpace.point(view, new Vector3f(end))).distance(end) < 1e-5);
                var shift = new Vector3f(side * -.1f, .22f, -.2f);
                var item = new Matrix4f(view).invert().translate(.56f, -.52f, -.72f).rotateY(.7f);
                var before = view.transformPosition(item.transformPosition(new Vector3f()));
                var delta = SpiritHandSpace.offset(view, new Vector3f(shift));
                item.translateLocal(delta.x, delta.y, delta.z);
                var after = view.transformPosition(item.transformPosition(new Vector3f()));
                assertTrue(after.sub(before).distance(shift) < 1e-5);
            }
        }
    }
}
