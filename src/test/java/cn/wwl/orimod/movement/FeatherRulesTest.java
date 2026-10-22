package cn.wwl.orimod.movement;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FeatherRulesTest {
    @Test void humanNeverUsesFeatherAndSpiritNeedsSkill(){
        assertFalse(FeatherRules.permitted(false,false,true,false));assertFalse(FeatherRules.permitted(false,true,false,false));
        assertTrue(FeatherRules.permitted(true,true,false,false));assertFalse(FeatherRules.permitted(true,false,true,false));
        assertFalse(FeatherRules.permitted(true,true,true,true));assertFalse(FeatherRules.permitted(false,false,true,true));
    }
    @Test void equipmentUpgradeIsFasterAndFallsSlower(){
        Vec3 a=Vec3.ZERO,b=Vec3.ZERO;for(int i=0;i<120;i++){
            a=FeatherRules.velocity(a.add(0,-.08,0),new Vec3(1,0,0),false,false);
            b=FeatherRules.velocity(b.add(0,-.08,0),new Vec3(1,0,0),true,false);
        }
        assertEquals(.5,a.x,1e-6);assertEquals(.65,b.x,1e-6);assertEquals(-.12,a.y,1e-6);assertEquals(-.07,b.y,1e-6);
    }
    @Test void divingNeverConvertsVerticalSpeedToForwardSpeed(){
        var a=FeatherRules.velocity(new Vec3(.3,-.1,0),Vec3.ZERO,false,false);
        var b=FeatherRules.velocity(new Vec3(.3,-8,0),Vec3.ZERO,false,false);
        assertEquals(a.x,b.x);assertEquals(-.12,b.y,1e-6);
    }
    @Test void oldFlightVelocityDecaysInsteadOfInstantClamp(){
        var v=FeatherRules.velocity(new Vec3(2,-1,0),Vec3.ZERO,false,false);assertEquals(1.72,v.x,1e-6);
        for(int i=0;i<60;i++)v=FeatherRules.velocity(v,Vec3.ZERO,false,false);assertTrue(v.horizontalDistance()<.5);
    }
}
