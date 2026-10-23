package cn.wwl.orimod.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.wwl.orimod.combat.HammerSlam.*;

class HammerSlamTest {
    @Test void animationDurationDoesNotFinishAirSlam() {
        for (int age : new int[]{0,15,30,100,199}) assertEquals(End.FALLING,end(false,false,false,true,age));
        assertEquals(End.LAND,end(true,false,false,true,160));
    }
    @Test void abandonedSlamCannotKeepMovementLocked() {
        assertEquals(End.CANCEL,end(false,true,false,true,10));
        assertEquals(End.CANCEL,end(false,false,true,true,10));
        assertEquals(End.CANCEL,end(false,false,false,false,10));
        assertEquals(End.CANCEL,end(false,false,false,true,TIMEOUT));
        assertEquals(End.CANCEL,end(false,false,false,true,-1));
    }
    @Test void clientDeadlineBoundsLostStopPacket() {
        var view=new CombatView(1,MODE,0,100);
        assertTrue(falling(view,100)); assertTrue(falling(view,299));
        assertFalse(falling(view,300)); assertFalse(falling(view,99)); assertFalse(falling(CombatView.IDLE,100));
    }
}
