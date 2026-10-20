package cn.wwl.orimod.fx;

import cn.wwl.orimod.config.VisualQuality;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashLightTest {
    @Test void fadesInFiveTicksAndOutInEightWithInterpolation() {
        var light = new FlashLight();
        for (int i = 1; i <= 5; i++) { light.tick(true); assertEquals(i / 5f, light.amount(1), 1e-6); }
        light.tick(true); assertEquals(1, light.amount(.5f), 1e-6);
        light.tick(false);
        assertEquals(.9375, light.amount(.5f), 1e-6);
        for (int i = 1; i < 8; i++) light.tick(false);
        assertEquals(0, light.amount(1), 1e-6);
        light.tick(false); assertTrue(light.ended());
    }
    @Test void restartingDuringFadeDoesNotJump() {
        var light = new FlashLight();
        for (int i = 0; i < 5; i++) light.tick(true);
        light.tick(false);
        float before = light.amount(1);
        light.tick(true);
        assertEquals(before, light.amount(0), 1e-6);
        assertEquals(1, light.amount(1), 1e-6);
    }
    @Test void exposureRespectsSettingsAndNeverExceedsEightPercent() {
        assertEquals(.08, FlashLight.exposure(10, VisualQuality.DETAILED, true, 200), 1e-6);
        assertEquals(0, FlashLight.exposure(1, VisualQuality.FLUENT, true, 100));
        assertEquals(0, FlashLight.exposure(1, VisualQuality.BALANCED, false, 100));
        assertEquals(0, FlashLight.exposure(1, VisualQuality.DETAILED, true, 0));
        assertEquals(.032, FlashLight.exposure(1, VisualQuality.BALANCED, true, 40), 1e-6);
    }
}
