package cn.wwl.orimod.client.render;

import org.joml.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpiritAttackPoseTest {
    static SpiritRigFixture rig;
    @BeforeAll static void load() throws Exception {rig=new SpiritRigFixture();}

    @Test void worldTimeResyncDoesNotSkipAFrameWithinOneSwing() {
        var timeline=new SpiritAttackPose.Timeline();
        assertEquals(1.51f,timeline.elapsed(1,1920.51,1.51),1e-5);
        assertEquals(1.664f,timeline.elapsed(1,1920.664,2.664),1e-5,"A forward time packet must not skip the hit approach");
        assertEquals(1.805f,timeline.elapsed(1,1920.805,-.195),1e-5,"A backward time packet must not rewind a swing");
        assertEquals(.1f,timeline.elapsed(2,1926.1,.1),1e-5,"New stage synchronizes to its real server phase");
        assertEquals(.25f,timeline.elapsed(2,1926.25,1.25),1e-5);
        timeline.end();
        assertEquals(5f,timeline.elapsed(2,2000,5),1e-5,"Reobserved actions do not reuse an old local anchor");
    }

    @Test void actualGripHasForwardReadableArcAndContinuousRecovery() {
        float maxStep=0,minArc=1000;
        for(boolean left:new boolean[]{false,true})for(int stage=1;stage<=3;stage++) {
            var pose=new SpiritPoseLayer("test");pose.bind(rig.table);
            Vector3f last=null;float min=1000,max=-1000;
            for(int frame=0;frame<=40;frame++) {
                float phase=frame/40f;pose.clear();SpiritAttackPose.write(pose,phase,left,true,0,stage);pose.publish();
                var world=rig.world(pose);var tip=rig.tip(world,left);
                if(last!=null)maxStep=java.lang.Math.max(maxStep,(float)java.lang.Math.toDegrees(last.angle(tip)));
                last=tip;float angle=(float)java.lang.Math.atan2(tip.y,tip.z);min=java.lang.Math.min(min,angle);max=java.lang.Math.max(max,angle);
                assertTrue(java.lang.Math.abs(SpiritAttackPose.bodyYaw(phase,left))<=.20001);
                var palm=rig.point(world,left?"grip_r":"grip_l");
                assertTrue(palm.x*palm.x/(.15*.15)+palm.z*palm.z/(.13*.13)>1,"Palm penetrated torso: "+palm);
            }
            minArc=java.lang.Math.min(minArc,(float)java.lang.Math.toDegrees(max-min));
        }
        System.out.println("Actual rig attack: max adjacent direction="+maxStep+" deg; minimum blade arc="+minArc+" deg");
        assertTrue(maxStep<=25);assertTrue(minArc>=90);
    }

    @Test void earlyBufferedComboKeepsTheDisplayedGripContinuous() {
        float maximum=0;
        for(boolean left:new boolean[]{false,true}) {
            var transition=new SpiritAttackPose.Transition();var pose=new SpiritPoseLayer("chain");pose.bind(rig.table);
            Vector3f last=null;float now=0;
            for(int stage=1;stage<=3;stage++) {
                float low=180,high=-180;
                float duration=cn.wwl.orimod.combat.SpiritCombat.duration(stage);
                float end=stage<3?cn.wwl.orimod.combat.SpiritCombat.CHAIN_TICKS:duration;
                for(float ticks=0;ticks<end;ticks+=.15f) {
                    pose.clear();SpiritAttackPose.writeStrike(pose,ticks/duration,left,stage);
                    transition.apply(pose,stage,now);pose.publish();
                    var direction=rig.tip(rig.world(pose),left);
                    if(last!=null)maximum=java.lang.Math.max(maximum,(float)java.lang.Math.toDegrees(last.angle(direction)));
                    float angle=(float)java.lang.Math.toDegrees(java.lang.Math.atan2(direction.y,direction.z));
                    low=java.lang.Math.min(low,angle);high=java.lang.Math.max(high,angle);
                    last=direction;now+=.0075f;
                }
                assertTrue(high-low>=90,"Buffered stage "+stage+" lost its strike arc: "+(high-low));
            }
        }
        System.out.println("Early buffered three-stage combo max direction delta="+maximum+" deg");
        assertTrue(maximum<=25,"Buffered chains must not restart at a different wrist orientation");
    }

    @Test void everyWeaponStrikePointsDownDuringItsHitWindow() {
        float highest=-180;
        for(boolean left:new boolean[]{false,true})for(int stage=0;stage<=3;stage++) {
            float stageHigh=-180,stageLow=180;
            var pose=new SpiritPoseLayer("hit_window");pose.bind(rig.table);
            float duration=stage==0?6:cn.wwl.orimod.combat.SpiritCombat.duration(stage);
            float hit=stage==0?2:cn.wwl.orimod.combat.SpiritCombat.windup(stage);
            // The hammer's native left click applies damage immediately; ticks 2..4 are its visual stroke.
            for(float tick=hit;tick<hit+2;tick+=.15f) {
                pose.clear();SpiritAttackPose.writeStrike(pose,tick/duration,left,stage);pose.publish();
                var tip=rig.tip(rig.world(pose),left);
                float angle=(float)java.lang.Math.toDegrees(java.lang.Math.atan2(tip.y,tip.z));
                highest=java.lang.Math.max(highest,angle);
                stageHigh=java.lang.Math.max(stageHigh,angle);stageLow=java.lang.Math.min(stageLow,angle);
                assertTrue(tip.z>.5 && angle<=(stage==3?-45:-20),"No downward forward hit: stage="+stage+" left="+left+" tick="+tick+" angle="+angle+" axis="+tip);
            }
            System.out.println("Hit window stage="+stage+" left="+left+" angles="+stageLow+".."+stageHigh);
        }
        System.out.println("Least downward hit angle across hammer and three blade stages="+highest+" deg");
    }

    @Test void overheadFinisherRaisesBehindTheHeadBeforeItsDeeperStrike() {
        for(boolean left:new boolean[]{false,true}) {
            var pose=new SpiritPoseLayer("overhead");pose.bind(rig.table);
            float duration=cn.wwl.orimod.combat.SpiritCombat.duration(3);
            SpiritAttackPose.writeStrike(pose,2.1f/duration,left,3);pose.publish();
            var world=rig.world(pose);var tip=rig.tip(world,left);
            var palm=rig.point(world,left?"grip_r":"grip_l");
            System.out.println("Finisher raised axis="+tip+" grip="+palm);
            assertTrue(tip.y>.3f && tip.z<-.7f,"Finisher must point up and back above the head");
        }
    }

    @Test void downwardStrikesStayContinuousAtTwentyFramesPerSecondInSlowMotion() {
        float maximum=0,minimumArc=1000;
        for(boolean left:new boolean[]{false,true})for(int stage=0;stage<=3;stage++) {
            var pose=new SpiritPoseLayer("strike_frames");pose.bind(rig.table);
            float duration=stage==0?6:cn.wwl.orimod.combat.SpiritCombat.duration(stage);
            Vector3f last=null;float low=180,high=-180;
            for(float tick=0;tick<=duration+.001f;tick+=.15f) {
                pose.clear();SpiritAttackPose.writeStrike(pose,tick/duration,left,stage);pose.publish();
                var direction=rig.tip(rig.world(pose),left);
                if(last!=null)maximum=java.lang.Math.max(maximum,(float)java.lang.Math.toDegrees(last.angle(direction)));
                last=direction;float angle=(float)java.lang.Math.toDegrees(java.lang.Math.atan2(direction.y,direction.z));
                low=java.lang.Math.min(low,angle);high=java.lang.Math.max(high,angle);
            }
            minimumArc=java.lang.Math.min(minimumArc,high-low);
        }
        System.out.println("Downstroke max adjacent direction="+maximum+" deg; minimum side arc="+minimumArc+" deg");
        assertTrue(maximum<=25);assertTrue(minimumArc>=90);
    }

    @Test void actualForearmCrossSectionsKeepSeventyPercentAreaAcrossWholeSwing() {
        Vector3f[] bind=rig.skin(rig.rest);float minimum=1;
        for(boolean left:new boolean[]{false,true}) {
            String elbow=left?"forearm_r":"forearm_l", wrist=left?"hand_r":"hand_l";
            var origin=rig.point(rig.rest,elbow);var axis=rig.point(rig.rest,wrist).sub(origin);float length=axis.length();axis.normalize();
            var rings=new ArrayList<List<Cut>>();
            // Intersect actual mesh triangles with nine planes. Vertex bins skip sparse ring topology.
            for(int ring=1;ring<=9;ring++) {
                float target=ring/10f*length;var cuts=new ArrayList<Cut>();
                for(int tri=0;tri<rig.triangles.length;tri+=3)for(int edge=0;edge<3;edge++) {
                    int a=rig.triangles[tri+edge],b=rig.triangles[tri+(edge+1)%3];
                    float da=new Vector3f(bind[a]).sub(origin).dot(axis)-target,db=new Vector3f(bind[b]).sub(origin).dot(axis)-target;
                    if(da*db>=0)continue;
                    float t=da/(da-db);var point=new Vector3f(bind[a]).lerp(bind[b],t).sub(origin).fma(-target,axis);
                    if(point.length()<.07)cuts.add(new Cut(a,b,t));
                }
                assertTrue(cuts.size()>=6,"Missing forearm section "+ring+"; intersections="+cuts.size());
                rings.add(cuts);
            }
            var pose=new SpiritPoseLayer("skin_scan");pose.bind(rig.table);
            for(int style=-1;style<=3;style++)for(int frame=0;frame<=80;frame++) {
                pose.clear();
                if(style<0)SpiritAttackPose.write(pose,frame/80f,left,true,0,1);
                else SpiritAttackPose.writeStrike(pose,frame/80f,left,style);
                pose.publish();var world=rig.world(pose);var vertices=rig.skin(world);
                var forward=rig.point(world,wrist).sub(rig.point(world,elbow)).normalize();
                for(var ring:rings) {float ratio=area(vertices,ring,forward)/area(bind,ring,axis);minimum=java.lang.Math.min(minimum,ratio);
                    assertTrue(ratio>=.7,"section collapsed: style="+style+" side="+left+" phase="+frame/80f+" ratio="+ratio);}
                for(int bone:new int[]{left?SpiritPoseLayer.FOREARM_R:SpiritPoseLayer.FOREARM_L,left?SpiritPoseLayer.HAND_R:SpiritPoseLayer.HAND_L}) {
                    var delta=new Quaternionf();rig.table.restRotation(rig.table.slotOfName(SpiritPoseLayer.BONES[bone]),delta);
                    delta.invert().mul(pose.target(bone));
                    float roll=2*(float)java.lang.Math.atan2(delta.y,delta.w); // actual bone-local longitudinal +Y
                    assertTrue(java.lang.Math.abs(roll)<=java.lang.Math.toRadians(30),"Long-axis twist exceeds 30 degrees");
                }
            }
        }
        System.out.println("Actual skinned forearm minimum section area ratio="+minimum);
    }

    private record Cut(int a,int b,float t) { }
    private static float area(Vector3f[] vertices,List<Cut> ids,Vector3f normal) {
        var u=new Vector3f(normal).cross(0,1,0);if(u.lengthSquared()<.01)u.set(normal).cross(0,0,1);u.normalize();var v=new Vector3f(normal).cross(u).normalize();
        var points=new ArrayList<Vector2f>();for(var cut:ids) {var p=new Vector3f(vertices[cut.a]).lerp(vertices[cut.b],cut.t);points.add(new Vector2f(p.dot(u),p.dot(v)));}
        points.sort(Comparator.<Vector2f>comparingDouble(p->p.x).thenComparingDouble(p->p.y));
        var hull=new ArrayList<Vector2f>();for(var p:points){while(hull.size()>1&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.removeLast();hull.add(p);}
        int low=hull.size();for(int i=points.size()-2;i>=0;i--){var p=points.get(i);while(hull.size()>low&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.removeLast();hull.add(p);}
        float a=0;for(int i=1;i<hull.size();i++)a+=hull.get(i-1).x*hull.get(i).y-hull.get(i-1).y*hull.get(i).x;
        return java.lang.Math.abs(a)*.5f;
    }
    private static float cross(Vector2f a,Vector2f b,Vector2f c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
}
