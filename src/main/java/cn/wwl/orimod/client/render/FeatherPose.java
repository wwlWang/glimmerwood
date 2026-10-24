package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Feather socket transform, in player-relative world axes. */
public final class FeatherPose {
    private FeatherPose() {}
    public static Matrix4f matrix(Vector3f first, Vector3f second, Vector3f forward) {
        if (first == null || second == null || forward == null || !first.isFinite() || !second.isFinite() || !forward.isFinite()) return null;
        var right = new Vector3f(forward.z,0,-forward.x);
        if (right.lengthSquared()<1e-5f) return null;
        right.normalize();
        var axis = new Vector3f(second).sub(first);
        if (axis.lengthSquared()<.0025f) return null;
        if (axis.dot(right)<0) axis.negate();
        float span = axis.length(), flat = (float)Math.hypot(axis.x,axis.z);
        if (flat<.02f) return null;
        // The quill lies along the paws, with at most 18 degrees of bank during blend transitions.
        axis.y = Math.clamp(axis.y,-flat*.3249f,flat*.3249f);
        axis.normalize();
        // Broad feather face catches air above Ori. Forward fixes the otherwise ambiguous free roll.
        var across = new Vector3f(forward.x,0,forward.z).normalize();
        across.fma(-across.dot(axis),axis);
        if (across.lengthSquared()<1e-5f) return null;
        across.normalize();
        var normal = new Vector3f(across).cross(axis).normalize();
        if (normal.y<0) { across.negate(); normal.negate(); }
        var centre = new Vector3f(first).add(second).mul(.5f);
        float lengthScale = Math.clamp(span/.23f,.85f,1.8f);
        var origin = new Vector3f(centre).fma(-.135f*lengthScale,axis);
        return new Matrix4f().setColumn(0,new Vector4f(across.mul(3.2f),0))
                .setColumn(1,new Vector4f(axis.mul(lengthScale),0))
                .setColumn(2,new Vector4f(normal.mul(1.3f),0)).setColumn(3,new Vector4f(origin,1));
    }
}
