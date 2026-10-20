package cn.wwl.orimod.block;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** R96: Ori lantern timing and Minecraft light levels. Scheduled world behavior is covered by LanternGameTests. */
class SpiritLanternBlockTest {
    @Test void flareLastsFiveSeconds() { assertEquals(100, SpiritLanternBlock.LIT_TICKS); }
    @Test void restingLanternHasDimLight() { assertEquals(7, SpiritLanternBlock.lightLevel(false)); }
    @Test void ignitedLanternHasMaximumLight() { assertEquals(15, SpiritLanternBlock.lightLevel(true)); }
}
