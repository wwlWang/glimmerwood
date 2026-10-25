package cn.wwl.orimod.client.render;

import cn.wwl.orimod.client.fx.BladeSlash;
import cn.wwl.orimod.combat.SpiritCombat;
import org.joml.Matrix4f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SpiritBladeFramingTest {
    private static float rad(float degrees) { return (float)Math.toRadians(degrees); }
    static SpiritBladeFraming.Projection projection(boolean left, SpiritFirstPersonMotion.Pose pose) {
        var p=pose.side(left);int side=left?-1:1;
        var input=new Matrix4f().translation(side*.56f+p.x(),-.465f+p.y(),-.745f+p.z())
                .rotateY(rad(p.yaw())).rotateZ(rad(p.roll())).rotateX(rad(p.pitch()));
        // Vanilla ItemTransform applies left-handed signs to the left display's translation/Y/Z rotations.
        var display=new Matrix4f().translation(side*.03125f,.125f,.0625f).rotateY(rad(-90)).rotateZ(rad(25)).scale(.48f);
        var fit=SpiritBladeFraming.fit(left,input,display,true);
        assertTrue(fit.gripError()<1e-5f,"handle contact");
        assertTrue(fit.arm().wristDegrees()<=55.001f,"wrist limit");
        return SpiritBladeFraming.project(new Matrix4f(fit.item()).mul(display),1920f/1015);
    }
    static SpiritFirstPersonMotion.Pose pose(int stage,float elapsed,SpiritFirstPersonMotion.Pose from) {
        int wind=SpiritCombat.windup(stage),hit=SpiritCombat.recoveryStart(stage)-wind;
        return SpiritFirstPersonMotion.blade(stage,elapsed,wind,hit,SpiritCombat.duration(stage)-wind-hit,from);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void firstWindupTurnsTheActualBladeAtLeastThirtyDegrees(boolean left) {
        var idle=projection(left,SpiritFirstPersonMotion.Pose.REST);
        var cock=projection(left,pose(1,SpiritCombat.windup(1),SpiritFirstPersonMotion.Pose.REST));
        assertTrue(Math.abs(cock.angle()-idle.angle())>=30,"actual projected wind-up delta="+Math.abs(cock.angle()-idle.angle()));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void secondWindupTurnsTheActualBladeAtLeastThirtyDegrees(boolean left) {
        var idle=projection(left,SpiritFirstPersonMotion.Pose.REST);
        var cock=projection(left,pose(2,SpiritCombat.windup(2),SpiritFirstPersonMotion.Pose.REST));
        assertTrue(Math.abs(cock.angle()-idle.angle())>=30,"actual projected wind-up");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void overheadWindupStaysAwayFromNearPlaneAndBelowSizeLimit(boolean left) {
        var idle=projection(left,SpiritFirstPersonMotion.Pose.REST);
        var from=pose(2,SpiritCombat.CHAIN_TICKS,SpiritFirstPersonMotion.Pose.REST);
        for(float t=0;t<=SpiritCombat.windup(3);t+=.05f) {
            var b=projection(left,pose(3,t,from));
            assertTrue(b.nearClearance()>=.25f,"near clearance at "+t+": "+b.nearClearance());
            assertTrue(b.area()<=idle.area()*2.5f,"area ratio at "+t+": "+b.area()/idle.area());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void fullOverheadWindupShowsTheBladeAndTip(boolean left) {
        var b=projection(left,pose(3,SpiritCombat.windup(3),SpiritFirstPersonMotion.Pose.REST));
        assertTrue(b.visible()>=.4f,"visible proportion="+b.visible());
        assertTrue(Math.abs(b.tipX())<1&&Math.abs(b.tipY())<1,"blade tip in frame: "+b.tipX()+","+b.tipY());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void entireChainedComboKeepsMaximumGlowBladeAwayFromCamera(boolean left) {
        var from=SpiritFirstPersonMotion.Pose.REST;
        var idle=projection(left,from);
        for(int stage=1;stage<=3;stage++) {
            for(float t=0;t<=SpiritCombat.duration(stage);t+=.05f) {
                var b=projection(left,pose(stage,t,from));
                assertTrue(b.nearClearance()>=.25f,"stage "+stage+" t="+t+" near="+b.nearClearance());
                assertTrue(b.area()<=idle.area()*2.5f,"stage "+stage+" t="+t+" area="+b.area()/idle.area());
            }
            from=pose(stage,SpiritCombat.CHAIN_TICKS,from);
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void remoteArcKeepsItsTail(int stage) {
        float t=SpiritCombat.recoveryStart(stage)+1;
        assertEquals(6f/7,BladeSlash.opacity(stage,t,false),1e-6);
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void firstPersonArcHasNoRecoveryPixels(int stage) {
        // The renderer selects this envelope only for the local first-person view.
        for(float t=SpiritCombat.recoveryStart(stage);t<SpiritCombat.duration(stage);t+=.1f)
            assertEquals(0,BladeSlash.opacity(stage,t,true),1e-6,"no FP arc during recovery at "+t);
    }
}
