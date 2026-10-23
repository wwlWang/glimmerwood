package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BashSelectionTest {
    private final Vec3 forward = new Vec3(0,0,1);
    @Test void behindAndSideRemainReachable() {
        assertTrue(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(0,0,-2),null));
        assertTrue(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(3,0,0),null));
        assertFalse(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(0,0,-6.001),null));
    }
    @Test void clearlyNearerWinsEvenAgainstPerfectAlignment() {
        assertTrue(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(0,0,-1),new Vec3(0,0,3)));
        assertFalse(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(0,0,3),new Vec3(0,0,-1)));
    }
    @Test void facingBreaksEqualDistanceTies() {
        assertTrue(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(0,0,2),new Vec3(0,0,-2)));
        assertFalse(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(0,0,-2),new Vec3(0,0,2)));
    }
    @Test void forwardChainWinsComparableDistance() {
        assertTrue(SpiritTargets.preferred(Vec3.ZERO,forward,new Vec3(0,0,3),new Vec3(0,0,-2.5)));
    }
    @Test void aimLastsOnePointEightSeconds() { assertEquals(36,BashSession.AIM_TICKS); }
}
