package cn.wwl.orimod.client.fx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryViewTest {
    @Test void bottomGlowUsesAPercentSettingWithoutAmplification() {
        assertEquals(.035f,RecoveryView.alpha(.035f,100),.00001f);
        assertEquals(.0175f,RecoveryView.alpha(.035f,50),.00001f);
        assertEquals(0,RecoveryView.alpha(.035f,0));
    }

    @Test void nearCameraHealCannotFillTheView() {
        for(double distance:new double[]{.3,.5,.7,1,2}) {
            assertTrue(RecoveryView.count(48,distance)<=4, "Near-view motes must be sparse");
            assertTrue(RecoveryView.size(.26f,distance)/distance<=.045, "Projected mote size obscures the view");
        }
    }
    @Test void approachingTheCameraReducesBothSizeAndCount() {
        assertTrue(RecoveryView.count(48,.3)<RecoveryView.count(48,1));
        assertTrue(RecoveryView.size(.26f,.3)<RecoveryView.size(.26f,1));
        assertEquals(0,RecoveryView.count(48,.1));
        assertEquals(0,RecoveryView.count(0,1));
    }
    @Test void particlesStayBesideHandsOutsideReticleAndAboveBottomEdge() {
        for(int i=0;i<4;i++)for(int t=0;t<100;t++){
            var p=RecoveryView.point(i,t*.25);
            assertTrue(Math.abs(p.side())>=.28 && Math.abs(p.side())<=.36);
            assertTrue(p.down()/p.forward()>.4 && p.down()/p.forward()<.55);
        }
    }
}
