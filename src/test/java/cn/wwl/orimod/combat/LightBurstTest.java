package cn.wwl.orimod.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** R39: Light Burst throw rule. */
class LightBurstTest {
    @Test void refusals() {
        assertNull(LightBurst.refusal(true, true, LightBurst.COST, 5, 5));
        assertEquals("not_spirit", LightBurst.refusal(false, true, 100, 0, 0));
        assertEquals("locked", LightBurst.refusal(true, false, 100, 0, 0));
        assertEquals("cooldown", LightBurst.refusal(true, true, 100, 4, 5));
        assertEquals("energy", LightBurst.refusal(true, true, LightBurst.COST - .1f, 0, 0));
    }
}
