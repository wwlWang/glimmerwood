package cn.wwl.orimod.fx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestorationLookGuideTest {
    @Test void smoothOneSecondThenReleases(){
        var g=new RestorationLookGuide();g.start(10,30,30,-30,true);
        assertEquals(30,g.advance(0,0,0,true).orElseThrow().pitch());
        var half=g.advance(10,0,0,true).orElseThrow();assertEquals(20,half.yaw());assertEquals(0,half.pitch());
        assertEquals(-30,g.advance(20,0,0,true).orElseThrow().pitch());assertFalse(g.active());
        assertTrue(g.advance(21,0,0,true).isEmpty());
    }
    @Test void shortestYawArcCrossesNorthWithoutFullSpin(){
        var g=new RestorationLookGuide();g.start(179,0,-179,0,true);
        assertEquals(180,g.advance(10,0,0,true).orElseThrow().yaw());assertEquals(181,g.advance(20,0,0,true).orElseThrow().yaw());
    }
    @Test void anyMouseMovementImmediatelyCancelsAndNeverResumes(){
        for(var delta:new double[][]{{.000001,0},{0,-.000001},{5,6}}){
            var g=new RestorationLookGuide();g.start(0,30,0,-30,true);g.advance(5,0,0,true);
            assertTrue(g.advance(6,delta[0],delta[1],true).isEmpty());assertTrue(g.cancelled());
            for(int tick=7;tick<300;tick++)assertTrue(g.advance(tick,0,0,true).isEmpty());
        }
    }
    @Test void thirdPersonStartNeverAcquiresViewEvenAfterSwitchingBack(){
        var g=new RestorationLookGuide();g.start(0,30,0,-30,false);
        assertTrue(g.advance(1,0,0,false).isEmpty());assertTrue(g.advance(2,0,0,true).isEmpty());
    }
    @Test void switchingToThirdPersonCancelsPermanently(){
        var g=new RestorationLookGuide();g.start(0,30,0,-30,true);
        assertTrue(g.advance(2,0,0,false).isEmpty());assertTrue(g.advance(3,0,0,true).isEmpty());assertTrue(g.cancelled());
    }
    @Test void disconnectOrEndClearsAndANewSessionCanStart(){
        var g=new RestorationLookGuide();g.start(0,30,0,-30,true);g.advance(1,1,0,true);g.clear();
        assertFalse(g.active());assertFalse(g.cancelled());assertTrue(g.advance(2,0,0,true).isEmpty());
        g.start(0,30,0,-30,true);assertTrue(g.advance(1,0,0,true).isPresent());
    }
}
