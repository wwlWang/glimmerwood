package cn.wwl.orimod.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritWeaponUseTest {
    @Test void refusesInsufficientEnergyAndCooldownBeforeAnimation() {
        for (float cost : new float[]{8, 12}) {
            assertTrue(SpiritWeaponUse.blocked(cost, cost - .01f, false));
            assertTrue(SpiritWeaponUse.blocked(cost, cost, true));
            assertFalse(SpiritWeaponUse.blocked(cost, cost, false));
        }
        assertFalse(SpiritWeaponUse.blocked(0, 0, true));
    }
    @Test void heldRetryOnlyNotifiesOnceUntilRelease() {
        var latch = new SpiritWeaponUse.FeedbackLatch();
        assertTrue(latch.refuse());
        for (int i = 0; i < 100; i++) assertFalse(latch.refuse());
        latch.release();
        assertTrue(latch.refuse());
    }
}
