package cn.wwl.orimod.world;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChallengeGhostTest {
    @Test void recordsEveryStepUpToTheLimit() {
        assertTrue(ChallengeGhost.shouldRecord(0, 0));
        assertFalse(ChallengeGhost.shouldRecord(1, 1));
        assertTrue(ChallengeGhost.shouldRecord(2, 1));
        assertFalse(ChallengeGhost.shouldRecord(2, ChallengeGhost.MAX_SAMPLES));
    }

    @Test void replaysByElapsedTimeAndEnds() {
        var trace = List.of(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(2, 0, 0));
        assertEquals(new Vec3(0, 0, 0), ChallengeGhost.at(trace, 1));
        assertEquals(new Vec3(2, 0, 0), ChallengeGhost.at(trace, 5));
        assertNull(ChallengeGhost.at(trace, 6));
        assertNull(ChallengeGhost.at(List.of(), 0));
        assertNull(ChallengeGhost.at(null, 0));
    }

    @Test void onlyABetterTimeReplacesTheGhost() {
        assertTrue(ChallengeGhost.replaces(Integer.MAX_VALUE, false, 900));
        assertTrue(ChallengeGhost.replaces(500, true, 499));
        assertFalse(ChallengeGhost.replaces(500, true, 500));
        assertTrue(ChallengeGhost.replaces(400, false, 500));
    }
}
