package cn.wwl.orimod.combat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class CombatTimelineTest {
    @ParameterizedTest @CsvSource({
            "1,0,windup", "1,1,windup", "1,2,hit", "1,3,hit", "1,4,recovery", "1,11,recovery",
            "2,0,windup", "2,2,windup", "2,3,hit", "2,4,hit", "2,5,recovery", "2,12,recovery",
            "3,0,windup", "3,3,windup", "3,4,hit", "3,5,hit", "3,6,recovery", "3,11,recovery"})
    void phasesChangeAtExactLogicalTicks(int stage, long tick, String expected) {
        assertEquals(expected, SpiritCombat.phase(stage, tick));
    }
    @ParameterizedTest @CsvSource({"1,2,4,12", "2,3,5,13", "3,4,6,12"})
    void comboWindowsAreFiniteAndNonOverlapping(int stage, int windup, int recovery, int end) {
        assertEquals(windup, SpiritCombat.windup(stage)); assertEquals(recovery, SpiritCombat.recoveryStart(stage));
        assertEquals(end, SpiritCombat.duration(stage));
        long hits = java.util.stream.IntStream.range(0, end).filter(t -> SpiritCombat.phase(stage, t).equals("hit")).count();
        assertEquals(2, hits);
    }
    @ParameterizedTest @CsvSource({"-1,1", "0,1", "4,3", "2147483647,3"})
    void invalidStageIsBounded(int stage, int fallback) {
        assertEquals(SpiritCombat.duration(fallback), SpiritCombat.duration(stage));
    }
}
