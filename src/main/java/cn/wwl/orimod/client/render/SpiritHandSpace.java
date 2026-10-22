package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Hand vertices still pass through ModelView: convert screen-relative offsets back into that input space. */
public final class SpiritHandSpace {
    private SpiritHandSpace() { }

    public static Vector3f point(Matrix4fc view, Vector3f point) {
        return new Matrix4f(view).invert().transformPosition(point);
    }

    public static Vector3f offset(Matrix4fc view, Vector3f offset) {
        return new Matrix4f(view).invert().transformDirection(offset);
    }
}
