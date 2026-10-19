package cn.wwl.orimod.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** R37: companion Spirit Flame firing rule. */
class SpiritFlameTest {
    @Test void firesOnlyForLiveSpiritsOffCooldown() {
        assertTrue(SpiritFlame.ready(true, true, false, 20, 20));
        assertFalse(SpiritFlame.ready(false, true, false, 20, 0));
        assertFalse(SpiritFlame.ready(true, false, false, 20, 0));
        assertFalse(SpiritFlame.ready(true, true, true, 20, 0));
        assertFalse(SpiritFlame.ready(true, true, false, 19, 20));
    }

    @Test void tuning() {
        assertEquals(6, SpiritFlame.RANGE);
        assertTrue(SpiritFlame.COOLDOWN_TICKS >= 10, "flame must not out-damage the blade");
    }
}
