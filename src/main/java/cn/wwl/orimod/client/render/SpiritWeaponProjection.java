package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Development-only projection of the actual crossed ribbons and maximum charge glow. No shipping-frame caller. */
final class SpiritWeaponProjection {
    private SpiritWeaponProjection() { }
    record Bounds(float minX,float minY,float maxX,float maxY,float near,float visible,float axis,float tipX,float tipY,float gripX,float gripY) {
        float area() { return (maxX-minX)*(maxY-minY); }
    }
    static Bounds measure(SpiritFirstPersonGrip.Kind kind,Matrix4f matrix,float pull,float aspect) {
        var points=geometry(kind,pull);float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX,near=minX;int visible=0;
        for(var point:points) {
            matrix.transformPosition(point.sub(.5f,.5f,.5f));near=Math.min(near,-point.z-.05f);
            float x=point.x/(-point.z*.7002075f*aspect),y=point.y/(-point.z*.7002075f);
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            if(Math.abs(x)<=1&&Math.abs(y)<=1&&point.z<-.05f)visible++;
        }
        var a=matrix.transformPosition(new Vector3f(-.4f,-.4f,0));var b=matrix.transformPosition(new Vector3f(.4f,.4f,0));
        float axis=(float)Math.toDegrees(Math.atan2(-(b.y/-b.z-a.y/-a.z),b.x/-b.z-a.x/-a.z));
        var tip=matrix.transformPosition(kind==SpiritFirstPersonGrip.Kind.HAMMER?new Vector3f(.24f,.24f,0):new Vector3f(.5f,.5f,0));
        var grip=matrix.transformPosition(SpiritWeaponRig.grip(kind));
        return new Bounds(minX,minY,maxX,maxY,near,visible/(float)points.size(),axis,
                tip.x/(-tip.z*.7002075f*aspect),tip.y/(-tip.z*.7002075f),grip.x/(-grip.z*.7002075f*aspect),grip.y/(-grip.z*.7002075f));
    }
    private static List<Vector3f> geometry(SpiritFirstPersonGrip.Kind kind,float pull) {
        var p=new ArrayList<Vector3f>();
        if(kind==SpiritFirstPersonGrip.Kind.BOW) {
            for(int i=0;i<20;i++)beam(p,SpiritWeaponShape.bowLimb(-1+i*.1f),SpiritWeaponShape.bowLimb(-.9f+i*.1f),.34f,SpiritWeaponShape.bowSide(-1+i*.1f));
            var nock=SpiritWeaponShape.bowString(pull,true);
            for(float t:new float[]{-1,1}) { var tip=SpiritWeaponShape.bowLimb(t);beam(p,nock,tip,.035f,new Vector3f(tip).sub(nock).cross(0,0,1));orb(p,tip,.13f); }
            orb(p,new Vector3f(SpiritWeaponShape.BOW_X,SpiritWeaponShape.BOW_Y,.5f),.11f);
            if(pull>0)beam(p,nock,new Vector3f(SpiritWeaponShape.BOW_X-.25f,SpiritWeaponShape.BOW_Y+.25f,.5f),.045f,new Vector3f(1,1,0));
        } else if(kind==SpiritFirstPersonGrip.Kind.HAMMER) {
            beam(p,new Vector3f(.10f,.10f,.5f),new Vector3f(.74f,.74f,.5f),.44f,new Vector3f(-1,1,0));
            beam(p,new Vector3f(.48f,1,.5f),new Vector3f(1,.48f,.5f),.52f,new Vector3f(1,1,0));
            orb(p,new Vector3f(.74f,.74f,.5f),.30f+.12f*pull);
        } else {
            beam(p,new Vector3f(.02f,.02f,.5f),new Vector3f(.82f,.82f,.5f),.38f,new Vector3f(-1,1,0));
            beam(p,new Vector3f(.70f,.70f,.5f),new Vector3f(1,1,.5f),.17f,new Vector3f(-1,1,0));
            orb(p,new Vector3f(.9f,.9f,.5f),.16f);
        }
        return p;
    }
    private static void beam(List<Vector3f> points,Vector3f a,Vector3f b,float width,Vector3f side) {
        var across=new Vector3f(side).normalize();var other=new Vector3f(b).sub(a).cross(side).normalize();
        for(int t=0;t<=12;t++)for(int s=-2;s<=2;s++)for(var direction:new Vector3f[]{across,other})
            points.add(new Vector3f(a).lerp(b,t/12f).fma(s*width/4,direction));
    }
    private static void orb(List<Vector3f> points,Vector3f c,float r) {
        for(int a=0;a<3;a++)for(int u=-2;u<=2;u++)for(int v=-2;v<=2;v++) {
            var point=new Vector3f(c);point.setComponent(a,point.get(a)+u*r/2);point.setComponent((a+1)%3,point.get((a+1)%3)+v*r/2);points.add(point);
        }
    }
}
