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
        var hands = new Vector3f(second).sub(first);
        if (hands.lengthSquared()<.0025f) return null;
        if (hands.dot(right)<0) hands.negate();
        float flat = (float)Math.hypot(hands.x,hands.z);
        if (flat<.02f) return null;
        if(Math.abs(hands.dot(right))/flat<.15f)return null;
        // The two palms surround the lower quill; their line controls bank, not the long axis.
        // Vane trails behind the body at twenty degrees above horizontal, about 1.45 blocks long.
        var axis = new Vector3f(forward.x,0,forward.z).normalize().mul(-.9396926f);axis.y=.3420201f;
        float bank=Math.clamp((float)Math.atan2(hands.y,flat),-.20944f,.20944f);
        var across = new Vector3f(right).rotateAxis(bank,axis.x,axis.y,axis.z);
        var normal = new Vector3f(across).cross(axis).normalize();
        var centre = new Vector3f(first).add(second).mul(.5f);
        float lengthScale = 1.07f;
        var origin = new Vector3f(centre).fma(-.08f*lengthScale,axis);
        return new Matrix4f().setColumn(0,new Vector4f(across.mul(2.1f),0))
                .setColumn(1,new Vector4f(axis.mul(lengthScale),0))
                .setColumn(2,new Vector4f(normal.mul(1.3f),0)).setColumn(3,new Vector4f(origin,1));
    }
}
