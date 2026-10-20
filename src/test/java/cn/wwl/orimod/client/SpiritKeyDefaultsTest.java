package cn.wwl.orimod.client;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class SpiritKeyDefaultsTest {
    @Test void freeHStaysAndConflictsChooseTheFirstFreeAlternative() {
        assertEquals(GLFW.GLFW_KEY_H, SpiritKeyDefaults.flashKey(k -> false));
        assertEquals(GLFW.GLFW_KEY_J, SpiritKeyDefaults.flashKey(k -> k == GLFW.GLFW_KEY_H));
        assertEquals(GLFW.GLFW_KEY_K, SpiritKeyDefaults.flashKey(k -> k == GLFW.GLFW_KEY_H || k == GLFW.GLFW_KEY_J));
    }
    @Test void noFreeCandidateLeavesFlashUnboundRatherThanStealingAKey() {
        assertEquals(GLFW.GLFW_KEY_UNKNOWN, SpiritKeyDefaults.flashKey(k -> true));
    }
}
