package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Feather rooted in the main-side paw, with its tip in the opposite paw above the head. */
public final class FeatherPose {
    private FeatherPose() {}
    // Physical contacts on the original 1.35 m feather: bare quill and the outer shaft.
    // The last 0.33 m of vane extends beyond the supporting paw. Never shorten the mesh to fit a pose.
    static final Vector3f ROOT = rachis(.12f);
    static final Vector3f TIP = rachis(1.02f);
    private static Vector3f rachis(float y) {
        float s=y/1.35f;
        return new Vector3f(.0945f*(float)Math.sin(Math.PI*s)*s,y,.0675f*s*s);
    }
    public static Matrix4f matrix(Vector3f rootHand, Vector3f tipHand, Vector3f forward) {
        if(rootHand==null || tipHand==null || forward==null || !rootHand.isFinite() || !tipHand.isFinite() || !forward.isFinite())return null;
        var hands=new Vector3f(tipHand).sub(rootHand);
        if(hands.lengthSquared()<.0025f)return null;
        var axis=new Vector3f(hands).normalize();
        var across=new Vector3f(forward).fma(-forward.dot(axis),axis);
        if(across.lengthSquared()<.01f)return null;
        across.normalize();
        var normal=new Vector3f(across).cross(axis).normalize();
        if(normal.y<0) { across.negate();normal.negate(); }
        // A shallow camber catches the air; the hand line itself controls bank.
        across.rotateAxis((float)Math.toRadians(8),axis.x,axis.y,axis.z);
        normal.set(across).cross(axis).normalize();
        var sourceAxis=new Vector3f(TIP).sub(ROOT).normalize();
        var sourceAcross=new Vector3f(1,0,0).fma(-sourceAxis.x,sourceAxis).normalize();
        var sourceNormal=new Vector3f(sourceAcross).cross(sourceAxis);
        var source=new Matrix3f().setColumn(0,sourceAcross).setColumn(1,sourceAxis).setColumn(2,sourceNormal);
        var rotation=new Matrix3f().setColumn(0,across).setColumn(1,axis).setColumn(2,normal).mul(source.transpose());
        var origin=new Vector3f(rootHand).sub(rotation.transform(new Vector3f(ROOT)));
        return new Matrix4f().set(rotation).setColumn(3,new Vector4f(origin,1));
    }
}
