package cn.wwl.orimod.fx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ScreenFeedbackTest {
    @Test void highSlamShakeGrowsButRespectsSettingsAndGlobalCap() {
        var normal = new ScreenFeedback(); normal.stomp(100, 1);
        var high = new ScreenFeedback(); high.stomp(100, 1.375f);
        assertTrue(high.lastShakeAmplitude() > normal.lastShakeAmplitude());
        assertTrue(high.lastShakeAmplitude() <= ScreenFeedback.MAX_SHAKE_DEGREES);
        var off = new ScreenFeedback(); off.stomp(0, 1.375f);
        assertFalse(off.active());
    }
    private static double peakRoll(ScreenFeedback f) {
        double peak = 0;
        for (int t = 0; t < ScreenFeedback.SHAKE_TICKS + 2; t++) {
            for (double p = 0; p < 1; p += .1) peak = Math.max(peak, Math.abs(f.angles(p).roll()));
            f.tick();
        }
        return peak;
    }

    @Test void zeroSettingsProduceNoEffect() {
        var f = new ScreenFeedback();
        f.hurt(0, 0); f.stomp(0);
        assertFalse(f.active());
        assertTrue(f.angles(.5).isZero());
        assertEquals(0, f.flashAlpha(.5));
    }

    @Test void shakeOnlyDoesNotFlashAndFlashOnlyDoesNotShake() {
        var shake = new ScreenFeedback(); shake.hurt(100, 0);
        assertEquals(0, shake.flashAlpha(0));
        assertFalse(shake.angles(.3).isZero());
        var flash = new ScreenFeedback(); flash.hurt(0, 100);
        assertTrue(flash.angles(.3).isZero());
        assertEquals(ScreenFeedback.MAX_FLASH_ALPHA, flash.flashAlpha(0), 1e-9);
    }

    @ParameterizedTest @ValueSource(ints = {10, 30, 60, 100})
    void amplitudeScalesWithSettingAndNeverExceedsCap(int percent) {
        var f = new ScreenFeedback(); f.hurt(percent, percent);
        assertTrue(f.flashAlpha(0) <= ScreenFeedback.MAX_FLASH_ALPHA * percent / 100.0 + 1e-9);
        double peak = peakRoll(f);
        assertTrue(peak > 0);
        assertTrue(peak <= ScreenFeedback.MAX_SHAKE_DEGREES * percent / 100.0 + 1e-9, "peak " + peak);
    }

    @Test void lowerSettingGivesWeakerShake() {
        var low = new ScreenFeedback(); low.hurt(20, 0);
        var high = new ScreenFeedback(); high.hurt(80, 0);
        assertTrue(peakRoll(low) < peakRoll(high));
    }

    @Test void outOfRangePercentagesAreClamped() {
        assertEquals(0, ScreenFeedback.fraction(-50));
        assertEquals(1, ScreenFeedback.fraction(400));
        var f = new ScreenFeedback(); f.hurt(400, 400);
        assertEquals(ScreenFeedback.MAX_FLASH_ALPHA, f.flashAlpha(0), 1e-9);
    }

    @Test void effectsDecayToZeroWithinTheirWindows() {
        var f = new ScreenFeedback(); f.hurt(100, 100);
        for (int i = 0; i < Math.max(ScreenFeedback.SHAKE_TICKS, ScreenFeedback.FLASH_TICKS); i++) f.tick();
        assertFalse(f.active());
        assertTrue(f.angles(0).isZero());
        assertEquals(0, f.flashAlpha(0));
    }

    @Test void flashFadesMonotonically() {
        var f = new ScreenFeedback(); f.hurt(0, 100);
        double previous = Double.MAX_VALUE;
        for (int i = 0; i < ScreenFeedback.FLASH_TICKS; i++) {
            for (double p = 0; p < 1; p += .25) { double a = f.flashAlpha(p); assertTrue(a <= previous); previous = a; }
            f.tick();
        }
    }

    @Test void stompIsLighterThanHurtAndHasNoFlash() {
        var stomp = new ScreenFeedback(); stomp.stomp(100);
        var hurt = new ScreenFeedback(); hurt.hurt(100, 0);
        assertEquals(0, stomp.flashAlpha(0));
        assertTrue(peakRoll(stomp) < peakRoll(hurt));
    }

    @Test void weakerTriggerDoesNotCutStrongerShakeShort() {
        var f = new ScreenFeedback(); f.hurt(100, 100);
        f.tick();
        double before = Math.abs(f.angles(0).roll()) + Math.abs(f.angles(0).pitch());
        f.stomp(10);
        double after = Math.abs(f.angles(0).roll()) + Math.abs(f.angles(0).pitch());
        assertEquals(before, after, 1e-9);
    }

    @Test void clearStopsEverything() {
        var f = new ScreenFeedback(); f.hurt(100, 100);
        f.clear();
        assertFalse(f.active());
        assertTrue(f.angles(.5).isZero());
        assertEquals(0, f.flashAlpha(.5));
    }

    @Test void deterministicForSameInputs() {
        var a = new ScreenFeedback(); var b = new ScreenFeedback();
        a.hurt(50, 50); b.hurt(50, 50);
        a.tick(); b.tick();
        assertEquals(a.angles(.4), b.angles(.4));
    }
}
