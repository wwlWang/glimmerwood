package cn.wwl.orimod.block;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AltarRulesTest {
    @Test void oneFullDayStartsAtVictoryAndEndsExactlyAtDeadline() {
        assertEquals(24_000, AltarRules.COOLDOWN_TICKS);
        long ready = 1234 + AltarRules.COOLDOWN_TICKS;
        assertEquals(24_000, AltarRules.remaining(1234, ready));
        assertEquals(1, AltarRules.remaining(ready - 1, ready));
        assertEquals(0, AltarRules.remaining(ready, ready));
        assertEquals(0, AltarRules.remaining(ready + 1, ready));
    }
    @Test void legacyAltarIsReadyAndCountdownRoundsUp() {
        assertEquals(0, AltarRules.remaining(1234, 0));
        assertEquals(1200, AltarRules.seconds(24_000));
        assertEquals(1, AltarRules.seconds(1));
        assertEquals(2, AltarRules.seconds(21));
        assertEquals(0, AltarRules.seconds(-1));
    }
}
