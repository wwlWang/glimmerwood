package cn.wwl.orimod.client.render;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Camera-space two-bone arm. Palm and item receive the same correction so the grip never slides.
 * Paw +Y points to the fingers, +Z is the back of the hand. Units are blocks. */
public final class SpiritArmRig {
    public static final float UPPER=.36f, FOREARM=.38f, PAW_SCALE=1.18f;
    public static final float WRIST_LIMIT=55, TWIST_LIMIT=75;
    private static final float WRIST_OFFSET=.1f*PAW_SCALE;
    private SpiritArmRig() { }
    public record Frame(Vector3f shoulder, Vector3f elbow, Vector3f wrist, Vector3f palm, Quaternionf rotation) {
        // Measurements are lazy: ordinary IK/vertex rendering never evaluates these angles.
        public float elbowDegrees() {
            float cos = new Vector3f(shoulder).sub(elbow).normalize().dot(new Vector3f(wrist).sub(elbow).normalize());
            return (float)Math.toDegrees(Math.acos(Math.clamp(cos,-1,1)));
        }
        public float wristDegrees() {
            var axis = new Vector3f(wrist).sub(elbow).normalize();
            return (float)Math.toDegrees(Math.acos(Math.clamp(axis.dot(new Vector3f(0,1,0).rotate(rotation)),-1,1)));
        }
        public float twistDegrees() {
            var neutral = basis(new Vector3f(wrist).sub(elbow).normalize());
            var relative = neutral.invert().mul(rotation);
            float twist = (float)Math.toDegrees(2*Math.atan2(relative.y,relative.w));
            if(twist>180)twist-=360;if(twist < -180)twist+=360;
            return twist;
        }
    }

    public static Frame solve(boolean left, Vector3f requested, Quaternionf requestedRotation) {
        return solve(left,requested,requestedRotation,new Vector3f(left?.03425f:-.03425f,-.09f,.008f));
    }
    public static Frame solve(boolean left,Vector3f requested,Quaternionf requestedRotation,Vector3f wristSocket) {
        return solve(left,requested,requestedRotation,wristSocket,false);
    }
    private static Frame solve(boolean left,Vector3f requested,Quaternionf requestedRotation,Vector3f wristSocket,boolean twoHand) {
        return solve(left,requested,requestedRotation,wristSocket,twoHand,.66f);
    }
    private static Frame solve(boolean left,Vector3f requested,Quaternionf requestedRotation,Vector3f wristSocket,boolean twoHand,float outside) {
        float side=left?-1:1;
        // A drawn bow brings both shoulders inward; bone lengths and the elbow pole remain unchanged.
        var shoulder=new Vector3f(side*(twoHand?.30f:.50f),twoHand?-.60f:-.66f,-.22f);
        var palm=new Vector3f(requested);
        // Keep the hand at the lower edge; the weapon tip can still sweep above it.
        palm.z=Math.clamp(palm.z,-.84f,twoHand?-.31f:-.44f);
        palm.x=twoHand?Math.clamp(palm.x,-.66f,.66f):side*Math.clamp(side*palm.x,.34f,outside);
        float halfHeight=-palm.z*.7002075f; // vanilla hand projection, vertical FOV 70
        palm.y=Math.clamp(palm.y,-halfHeight*1.15f,-halfHeight*(twoHand?.35f:.55f));
        var reach=new Vector3f(palm).sub(shoulder);
        float maximum=UPPER+FOREARM-(twoHand||outside>.66f?.02f:WRIST_OFFSET+.005f);
        if(reach.length()>maximum)palm.set(shoulder).add(reach.normalize(maximum));
        // Solve bones once. Fit the complete palm basis to the wrist; the weapon follows that
        // small correction rather than stretching the forearm or iterating unstable wrist rolls.
        var wrist=new Vector3f(palm).add(new Vector3f(wristSocket).mul(PAW_SCALE));
        if((twoHand||outside>.66f)&&wrist.distanceSquared(shoulder)>(UPPER+FOREARM-.001f)*(UPPER+FOREARM-.001f))
            wrist.sub(shoulder).normalize(UPPER+FOREARM-.001f).add(shoulder);
        var elbow=new Vector3f();elbow(shoulder,wrist,side,elbow);
        var neutral=basis(new Vector3f(wrist).sub(elbow).normalize());
        var rotation=limited(neutral,requestedRotation);
        palm.set(wrist).sub(new Vector3f(wristSocket).mul(PAW_SCALE).rotate(rotation));
        return new Frame(shoulder,elbow,wrist,palm,rotation);
    }
    /** Surface contact permits a free palm orientation. Position-only correction converges without
     * changing the item's display rotation or scale; unreachable poses retain the ordinary reach limit. */
    static Frame surface(boolean left,Vector3f target,Quaternionf rotation) {
        return surface(left,target,rotation,new Vector3f(left?.03425f:-.03425f,-.09f,.008f));
    }
    static Frame surface(boolean left,Vector3f target,Quaternionf rotation,Vector3f socket) {
        return surface(left,target,rotation,socket,false);
    }
    static Frame surface(boolean left,Vector3f target,Quaternionf rotation,Vector3f socket,boolean twoHand) {
        return surface(left,target,rotation,socket,twoHand,.66f);
    }
    static Frame toolSurface(boolean left,Vector3f target,Quaternionf rotation) {
        return surface(left,target,rotation,new Vector3f(left?.03425f:-.03425f,-.09f,.008f),false,.86f);
    }
    private static Frame surface(boolean left,Vector3f target,Quaternionf rotation,Vector3f socket,boolean twoHand,float outside) {
        var request=new Vector3f(target);var frame=solve(left,request,rotation,socket,twoHand,outside);
        for(int i=0;i<8&&frame.palm().distanceSquared(target)>1e-10f;i++) {
            request.add(new Vector3f(target).sub(frame.palm()));frame=solve(left,request,rotation,socket,twoHand,outside);
        }
        return frame;
    }
    private static Quaternionf limited(Quaternionf neutral,Quaternionf requested) {
        var relative=new Quaternionf(neutral).invert().mul(requested).normalize();
        var axial=new Quaternionf(0,relative.y,0,relative.w);
        if(axial.lengthSquared()<1e-8f)axial.identity();else axial.normalize();
        if(axial.w<0)axial.mul(-1);
        float angle=2*(float)Math.atan2(axial.y,axial.w);
        angle=Math.clamp(angle,(float)Math.toRadians(-TWIST_LIMIT),(float)Math.toRadians(TWIST_LIMIT));
        var swing=new Quaternionf(relative).mul(new Quaternionf(axial).invert()).normalize();
        if(swing.w<0)swing.mul(-1);
        float swingAngle=2*(float)Math.acos(Math.clamp(swing.w,-1,1));
        float limit=(float)Math.toRadians(WRIST_LIMIT);
        if(swingAngle>limit)swing=new Quaternionf().slerp(swing,limit/swingAngle);
        return new Quaternionf(neutral).mul(swing).rotateY(angle).normalize();
    }
    static Quaternionf mirror(Quaternionf rotation) {
        return new Quaternionf(rotation.x,-rotation.y,-rotation.z,rotation.w);
    }
    static org.joml.Matrix4f correction(Vector3f oldPalm,Quaternionf oldRotation,Frame frame) {
        return new org.joml.Matrix4f().translation(frame.palm())
                .rotate(new Quaternionf(frame.rotation()).mul(new Quaternionf(oldRotation).invert()))
                .translate(new Vector3f(oldPalm).negate());
    }
    private static void elbow(Vector3f shoulder,Vector3f wrist,float side,Vector3f out) {
        var axis=new Vector3f(wrist).sub(shoulder);float distance=axis.length();axis.div(distance);
        float along=(UPPER*UPPER-FOREARM*FOREARM+distance*distance)/(2*distance);
        float height=(float)Math.sqrt(Math.max(0,UPPER*UPPER-along*along));
        var pole=new Vector3f(side,-1.4f,.15f);pole.fma(-pole.dot(axis),axis).normalize();
        out.set(shoulder).fma(along,axis).fma(height,pole);
    }
    static Quaternionf basis(Vector3f fingers) {
        var z=new Vector3f(0,0,1).fma(-fingers.z,fingers);
        if(z.lengthSquared()<1e-6f)z.set(1,0,0).fma(-fingers.x,fingers);
        z.normalize();var x=new Vector3f(fingers).cross(z).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,fingers).setColumn(2,z));
    }
}
