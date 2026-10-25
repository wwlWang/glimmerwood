package cn.wwl.orimod.client.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** First-person weapon contracts in camera space. Item coordinates are centred, as in ItemRenderer. */
final class SpiritWeaponRig {
    private SpiritWeaponRig() { }
    record Fit(SpiritArmRig.Frame main, SpiritArmRig.Frame string, Matrix4f item,
               float mainError, float stringError) { }

    static Vector3f grip(SpiritFirstPersonGrip.Kind kind) {
        return kind == SpiritFirstPersonGrip.Kind.HAMMER ? new Vector3f(-.2f,-.2f,0)
                : kind == SpiritFirstPersonGrip.Kind.SPEAR ? new Vector3f(-.06f,-.06f,0)
                : new Vector3f(SpiritWeaponShape.BOW_X-.5f,SpiritWeaponShape.BOW_Y-.5f,0);
    }
    static Vector3f socket(boolean left) { return new Vector3f(left?.015f:-.015f,-.035f,.012f); }
    static Vector3f contact(SpiritArmRig.Frame frame,boolean left) {
        return socket(left).mul(SpiritArmRig.PAW_SCALE).rotate(frame.rotation()).add(frame.palm());
    }

    static Fit tool(SpiritFirstPersonGrip.Kind kind,boolean left,Matrix4f input,Matrix4f display,boolean measure) {
        if(left) {
            // Reflect the whole assembly, not only yaw/roll: a diagonal hammer is not mirror-symmetric about its shaft.
            var mirror=new Matrix4f().scaling(-1,1,1);
            var rightInput=new Matrix4f(mirror).mul(input).mul(mirror);
            var rightDisplay=new Matrix4f(display).m30(-display.m30());
            var right=tool(kind,false,rightInput,rightDisplay,measure);
            var a=right.main();
            var arm=new SpiritArmRig.Frame(reflect(a.shoulder()),reflect(a.elbow()),reflect(a.wrist()),reflect(a.palm()),SpiritArmRig.mirror(a.rotation()));
            return new Fit(arm,null,new Matrix4f(mirror).mul(right.item()),right.mainError(),Float.NaN);
        }
        var point=display.transformPosition(grip(kind));
        var oldPoint=input.transformPosition(new Vector3f(point));
        var canonical=new Quaternionf().rotationY((float)Math.toRadians(kind==SpiritFirstPersonGrip.Kind.HAMMER?20:90))
                .rotateZ((float)Math.toRadians(kind==SpiritFirstPersonGrip.Kind.HAMMER?0:25));
        var rest=SpiritArmRig.basis(new Vector3f(-.35f,.9f,-.2f).normalize()).rotateY((float)Math.toRadians(25));
        var rotation=input.getUnnormalizedRotation(new Quaternionf()).mul(display.getUnnormalizedRotation(new Quaternionf()).mul(canonical.invert()).mul(rest).normalize());
        var origin=new Vector3f(oldPoint).sub(socket(left).mul(SpiritArmRig.PAW_SCALE).rotate(rotation));
        var arm=SpiritArmRig.toolSurface(left,origin,rotation);
        var target=contact(arm,left);
        var item=new Matrix4f().translation(target).rotate(new Quaternionf(arm.rotation()).mul(new Quaternionf(rotation).invert()))
                .translate(new Vector3f(oldPoint).negate()).mul(input).mul(display);
        return new Fit(arm,null,item,measure?item.transformPosition(grip(kind)).distance(target):Float.NaN,Float.NaN);
    }

    private static Vector3f reflect(Vector3f v) { return new Vector3f(-v.x,v.y,v.z); }

    /** Bow and both hands form one side/front assembly. The arrow/string plane is clear of the crosshair. */
    static Fit bow(boolean left,float raise,float pull,float kick,float equip,boolean measure) {
        float side=left?-1:1;
        var up=new Vector3f(.12f,.99f,-.03f).normalize();
        var back=new Vector3f(-.80f,.04f,.60f);back.fma(-back.dot(up),up).normalize();
        var x=new Vector3f(up).add(back).mul(.7071068f);
        var y=new Vector3f(up).sub(back).mul(.7071068f);
        var rotation=new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,new Vector3f(x).cross(y)));
        var centre=new Vector3f(side*(.36f-.02f*raise),-.30f+.05f*raise-equip*.6f,-.65f-.10f*kick);
        var palmRotation=SpiritArmRig.basis(new Vector3f(-.35f,.9f,-.2f).normalize()).rotateY((float)Math.toRadians(25));
        if(left)palmRotation=SpiritArmRig.mirror(palmRotation);
        var origin=new Vector3f(centre).sub(socket(left).mul(SpiritArmRig.PAW_SCALE).rotate(palmRotation));
        var arm=SpiritArmRig.surface(left,origin,palmRotation,socket(left),true);
        var target=contact(arm,left);
        var item=new Matrix4f().translation(target).scale(side,1,1).rotate(rotation).scale(.55f).translate(grip(SpiritFirstPersonGrip.Kind.BOW).negate());
        var nock=item.transformPosition(SpiritWeaponShape.bowString(pull,true).sub(.5f,.5f,.5f));
        var stringRotation=SpiritArmRig.basis(new Vector3f(side*.4f,.65f,-.65f).normalize());
        var stringOrigin=new Vector3f(nock).sub(socket(!left).mul(SpiritArmRig.PAW_SCALE).rotate(stringRotation));
        var string=SpiritArmRig.surface(!left,stringOrigin,stringRotation,socket(!left),true);
        return new Fit(arm,string,item,measure?item.transformPosition(grip(SpiritFirstPersonGrip.Kind.BOW)).distance(target):Float.NaN,
                measure?contact(string,!left).distance(nock):Float.NaN);
    }
}
