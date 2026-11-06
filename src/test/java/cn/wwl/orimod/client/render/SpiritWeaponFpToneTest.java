package cn.wwl.orimod.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** First-person weapons sit on the near plane; world halos and full tip length blow out. */
class SpiritWeaponFpToneTest {
    @Test void firstPersonShortensTheAuthoredTip() {
        assertEquals(.98f, SpiritWeaponRenderer.firstPersonTip(false, .98f), 1e-6f);
        assertTrue(SpiritWeaponRenderer.firstPersonTip(true, .98f) < .90f);
        assertTrue(SpiritWeaponRenderer.firstPersonTip(true, .98f) > .70f);
        assertEquals(.82f, SpiritWeaponRenderer.firstPersonTip(false, .82f), 1e-6f);
        assertTrue(SpiritWeaponRenderer.firstPersonTip(true, .82f) < .82f);
    }

    @Test void firstPersonTipStillOutrunsTheGuard() {
        float tip = SpiritWeaponRenderer.firstPersonTip(true, .98f);
        assertTrue(tip > .55f, "Guard sits near .43; the shortened tip must remain a blade");
    }
}
