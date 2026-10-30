package cn.wwl.orimod.client.render;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fits only the forearm behind an already animated vanilla grip. Never moves the paw or item. */
public final class SpiritArmRig {
    public static final float UPPER=.36f, FOREARM=.38f, PAW_SCALE=1.25f;
    private SpiritArmRig() { }
    public record Frame(Vector3f shoulder, Vector3f elbow, Vector3f wrist, Vector3f palm, Quaternionf rotation) { }

    /** Follow the complete animated grip without feeding joint limits back into the item.
     * The off-screen shoulder yields when a swing exceeds the resting reach; both bones
     * retain their length and the paw remains a rigid, uniformly scaled mesh. */
    static Frame follow(boolean left, Vector3f palm, Quaternionf rotation) {
        return follow(left,palm,rotation,new Vector3f(left?.03425f:-.03425f,-.09f,.008f));
    }
    static Frame follow(boolean left, Vector3f palm, Quaternionf rotation, Vector3f wristSocket) {
        float side=left?-1:1;
        rotation = new Quaternionf(rotation).normalize();
        var wrist=new Vector3f(wristSocket).mul(PAW_SCALE).rotate(rotation).add(palm);
        // Put the shoulder on a ray below the hand viewport, not on its side edge.
        // Intersect that ray with the arm's reach sphere. Choosing the farther root
        // keeps the upper arm descending behind the forearm instead of folding
        // back toward a near-camera shoulder at crosshair height.
        float lateral=Math.clamp(side*wrist.x/Math.max(.1f,-wrist.z),.35f,.85f);
        var ray=new Vector3f(side*lateral,-.95f,-1);
        float reach=.68f, dot=ray.dot(wrist), square=ray.lengthSquared();
        float discriminant=dot*dot-square*(wrist.lengthSquared()-reach*reach);
        float depth=Math.max(.1f,(dot+(float)Math.sqrt(Math.max(0,discriminant)))/square);
        var shoulder=new Vector3f(ray).mul(depth);
        // Extreme off-screen swing poses can lie beyond that ray. Only the unseen
        // shoulder yields; the item, palm, wrist socket and both bone lengths stay intact.
        var offset=new Vector3f(shoulder).sub(wrist);
        if(discriminant<0 || depth==.1f)shoulder.set(wrist).add(offset.normalize().mul(reach));
        var elbow=new Vector3f();elbow(shoulder,wrist,side,elbow);
        return new Frame(shoulder,elbow,wrist,new Vector3f(palm),new Quaternionf(rotation));
    }
    private static void elbow(Vector3f shoulder,Vector3f wrist,float side,Vector3f out) {
        var axis=new Vector3f(wrist).sub(shoulder);float distance=axis.length();axis.div(distance);
        float along=(UPPER*UPPER-FOREARM*FOREARM+distance*distance)/(2*distance);
        float height=(float)Math.sqrt(Math.max(0,UPPER*UPPER-along*along));
        var pole=new Vector3f(0,-1,.1f);pole.fma(-pole.dot(axis),axis);
        if(pole.lengthSquared()<1e-6f)pole.set(side,0,0).fma(-side*axis.x,axis);
        pole.normalize();
        out.set(shoulder).fma(along,axis).fma(height,pole);
    }
    static Quaternionf basis(Vector3f fingers) {
        var z=new Vector3f(0,0,1).fma(-fingers.z,fingers);
        if(z.lengthSquared()<1e-6f)z.set(1,0,0).fma(-fingers.x,fingers);
        z.normalize();var x=new Vector3f(fingers).cross(z).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,fingers).setColumn(2,z));
    }
}
