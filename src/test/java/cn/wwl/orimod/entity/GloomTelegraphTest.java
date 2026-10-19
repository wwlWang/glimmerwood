package cn.wwl.orimod.entity;

import static org.junit.jupiter.api.Assertions.*;

import cn.wwl.orimod.client.GloomSpitterRenderer;
import org.junit.jupiter.api.Test;

/** R48: the core swells steadily through the telegraph. */
class GloomTelegraphTest {
    @Test void coreGrowsUntilTheOrbLeaves() {
        assertEquals(.3f, GloomSpitterRenderer.coreScale(0), 1e-6);
        float last = GloomSpitterRenderer.coreScale(GloomSpitter.CHARGE_TICKS);
        for (int left = GloomSpitter.CHARGE_TICKS - 1; left >= 1; left--) {
            float now = GloomSpitterRenderer.coreScale(left);
            assertTrue(now > last); last = now;
        }
        assertTrue(last <= .55f);
    }
}
