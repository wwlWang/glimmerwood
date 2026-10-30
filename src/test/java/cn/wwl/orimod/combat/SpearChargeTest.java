package cn.wwl.orimod.combat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SpearChargeTest {
    @ParameterizedTest @CsvSource({"0,4,true", "1,4,true", "3,4,true", "4,4,false", "5,5,false", "1200,1200,false"})
    void tapWaitsForFourTicksAndHeldReleaseIsImmediate(int elapsed, int due, boolean defer) {
        // Use a large world time too: the threshold is relative to the press, not world startup.
        for (long start : new long[]{0, 12_345_678_901L})
            assertEquals(start + due, SpearCharge.releaseAt(start, start + elapsed));
        assertEquals(defer, SpearCharge.deferRelease(elapsed));
    }
}
