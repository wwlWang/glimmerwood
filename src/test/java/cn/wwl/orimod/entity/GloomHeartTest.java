package cn.wwl.orimod.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** R50: Gloom Heart pure rules. */
class GloomHeartTest {
    @Test void phaseChangesAtHalfHealth() {
        assertEquals(1, GloomHeart.phaseFor(120, 120));
        assertEquals(1, GloomHeart.phaseFor(61, 120));
        assertEquals(2, GloomHeart.phaseFor(60, 120));
    }
    @Test void secondPhaseIsHarder() {
        assertTrue(GloomHeart.volleyCount(2) > GloomHeart.volleyCount(1));
        assertTrue(GloomHeart.ringCount(2) > GloomHeart.ringCount(1));
        assertTrue(GloomHeart.restTicks(2) < GloomHeart.restTicks(1));
    }
    @Test void slamOnlyInPhaseTwoAndEveryAttackIsTelegraphed() {
        for (int i = 0; i < 12; i++) assertNotEquals(GloomHeart.SLAM, GloomHeart.nextAttack(i, 1));
        boolean slam = false;
        for (int i = 0; i < 12; i++) slam |= GloomHeart.nextAttack(i, 2) == GloomHeart.SLAM;
        assertTrue(slam);
        for (int a : new int[]{GloomHeart.VOLLEY, GloomHeart.RING, GloomHeart.SLAM}) assertTrue(GloomHeart.telegraph(a) >= 20);
    }
}
