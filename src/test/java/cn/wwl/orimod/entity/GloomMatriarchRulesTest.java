package cn.wwl.orimod.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static cn.wwl.orimod.entity.GloomMatriarch.*;
import static org.junit.jupiter.api.Assertions.*;

class GloomMatriarchRulesTest {
    @Test void phaseTwoAtHalfHealthAndHigher() {
        assertEquals(1, phaseFor(76, MAX_HEALTH)); assertEquals(2, phaseFor(75, MAX_HEALTH));
        assertTrue(hover(2) > hover(1));
        assertTrue(featherCount(2) > featherCount(1));
        assertTrue(restTicks(2) < restTicks(1));
    }

    @Test void phaseOneNeverGustsAndPhaseTwoDoes() {
        boolean gust1 = false, gust2 = false;
        for (int i = 0; i < 10; i++) { gust1 |= nextAttack(i, 1) == GUST; gust2 |= nextAttack(i, 2) == GUST; }
        assertFalse(gust1); assertTrue(gust2);
        assertEquals(FEATHERS, nextAttack(0, 1));
        assertEquals(DIVE_TELEGRAPH, nextAttack(0, 2));
    }

    @Test void everyAttackIsTelegraphed() {
        for (int a : new int[]{DIVE_TELEGRAPH, FEATHERS, GUST}) assertTrue(telegraph(a) >= 20, "attack " + a);
    }

    @Test void gustThrowsOutwardAndUp() {
        var v = gustVelocity(new Vec3(3, -2, 4));
        assertEquals(GUST_PUSH, Math.hypot(v.x, v.z), 1e-9);
        assertTrue(v.x > 0 && v.z > 0 && v.y > 0);
        assertEquals(GUST_PUSH, gustVelocity(Vec3.ZERO).x, 1e-9);
    }
}
