package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class P5PhysicsTest {
    private static final Vec3 FORWARD = new Vec3(0,0,1);
    @Test void ordinaryStoneRetainsRecordedAccelerationBrakingAndReverse() {
        assertFalse(FeelTuning.slippery(.6f));assertFalse(FeelTuning.slippery(.5f));
        Vec3 v=Vec3.ZERO;double[] recorded={.11,.22,.32,.32,0,-.11,-.22,-.32};
        for(int t=0;t<recorded.length;t++){
            Vec3 wish=t<4?FORWARD:t==4?Vec3.ZERO:FORWARD.scale(-1);
            v=FeelTuning.horizontal(v,wish,true,1,1,1);assertEquals(recorded[t],v.z,1e-10);
        }
    }
    @Test void iceCoastUsesActualFrictionAndExceedsSeventyPercentOfHumanBaseline() {
        for(float friction:new float[]{.98f,.989f}){
            assertTrue(FeelTuning.slippery(friction));Vec3 v=Vec3.ZERO;
            for(int t=0;t<60;t++){
                v=FeelTuning.slipperyHorizontal(v,FORWARD,1,1,friction);
                assertTrue(v.length()<=.32+1e-10);v=v.scale(friction*.91f);
            }
            double coast=0;
            for(int t=0;t<100;t++){
                v=FeelTuning.slipperyHorizontal(v,Vec3.ZERO,1,1,friction);
                if(v.length()<.003)v=Vec3.ZERO;coast+=v.length();v=v.scale(friction*.91f);
            }
            assertTrue(coast >= .7*(friction>.98f?2.52756:2.19931),"coast "+coast);
        }
    }
    @Test void slipperyAndOrdinaryTargetSpeedsReadAttributes() {
        for(double multiplier:new double[]{.15,.55,1,1.6,2}){
            for(boolean ground:new boolean[]{false,true}){
                Vec3 v=Vec3.ZERO;for(int i=0;i<80;i++)v=FeelTuning.horizontal(v,FORWARD,ground,1,1,multiplier);
                assertEquals(.32*multiplier,v.length(),1e-9);
            }
            Vec3 v=Vec3.ZERO;for(int t=0;t<100;t++){
                v=FeelTuning.slipperyHorizontal(v,FORWARD,1,multiplier,.989f);
                assertTrue(v.length()<=.32*multiplier+1e-9);v=v.scale(.989f*.91f);
            }
        }
    }
    @Test void abilityAndKnockbackMomentumIsNotMultipliedBySpeedAttribute() {
        Vec3 impulse=FORWARD.scale(3);
        assertEquals(impulse,FeelTuning.slipperyHorizontal(impulse,FORWARD,1,1.6,.989f));
        for(double m:new double[]{.15,1,1.6}){
            double speed=FeelTuning.horizontal(impulse,Vec3.ZERO,false,1,1,m).length();
            assertTrue(speed<3&&speed>2.9,"impulse should decay gently, never scale: "+speed);
        }
    }
    @Test void freeFallReferenceNeverReturnsAboveOriginAtAnyGravity() {
        for(double gravity:new double[]{.001,.01,.04,.08,.16,1})for(double height:new double[]{.01,1,3,30,300}){
            double speed=SlimeBounce.freeFallSpeed(height,gravity),rise=0;
            for(int t=0;t<20000;t++){
                speed=(speed-gravity)*.98;
                if(speed<=0)break;rise+=speed;
            }
            assertTrue(rise<=height+1e-9,"gravity="+gravity+" height="+height+" rise="+rise);
        }
    }
    @Test void thirtyBlockSlimeReferenceMatchesMeasuredVanilla() {
        double v=SlimeBounce.freeFallSpeed(30,.08),rise=0;
        while((v=(v-.08)*.98)>0)rise+=v;
        assertEquals(14.61413,rise,.02);
    }
    @Test void swimAttributeScalesExtraPropulsionCapButNotDash() {
        assertEquals(.0375,WaterMovement.swimVelocity(Vec3.ZERO,FORWARD,true,1.25).z,1e-12);
        assertEquals(.67,WaterMovement.swimVelocity(FORWARD.scale(.67),FORWARD,true,1.25).z,1e-12);
        assertEquals(.9,WaterMovement.swimVelocity(FORWARD.scale(.9),FORWARD,true,1.25).z,1e-12);
        assertEquals(.95,WaterMovement.dashVelocity(FORWARD).z,1e-12);
        assertEquals(14,WaterMovement.DASH_COOLDOWN);
    }
}
