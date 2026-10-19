package cn.wwl.orimod.progression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** R34: Soul Link creation and safety rules. */
class SoulLinkTest {
    @Test void readySpiritOnGroundWithEnergyMayLink() {
        assertEquals(Optional.empty(), SoulLink.refusal(true, true, false, false, 25, 100, 100));
    }

    @Test void refusalsAreSpecific() {
        assertEquals(Optional.of("not_spirit"), SoulLink.refusal(false, true, false, false, 100, 0, 0));
        assertEquals(Optional.of("challenge"), SoulLink.refusal(true, true, true, false, 100, 0, 0));
        assertEquals(Optional.of("unstable"), SoulLink.refusal(true, false, false, false, 100, 0, 0));
        assertEquals(Optional.of("unstable"), SoulLink.refusal(true, true, false, true, 100, 0, 0));
        assertEquals(Optional.of("cooldown"), SoulLink.refusal(true, true, false, false, 100, 99, 100));
        assertEquals(Optional.of("energy"), SoulLink.refusal(true, true, false, false, SoulLink.COST - .01f, 0, 0));
    }

    @Test void linkNeedsRoomAndFloor() {
        assertTrue(SoulLink.safe(true, true, true));
        assertFalse(SoulLink.safe(false, true, true));
        assertFalse(SoulLink.safe(true, false, true));
        assertFalse(SoulLink.safe(true, true, false));
    }
}
