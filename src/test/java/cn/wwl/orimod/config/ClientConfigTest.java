package cn.wwl.orimod.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real ModConfigSpec correction that NeoForge runs when it loads orimod-client.toml. */
class ClientConfigTest {
    private static CommentedConfig legacyV1() {
        var config = CommentedConfig.inMemory();
        config.set("keyDefaultsApplied", true);
        config.set("spiritCamera", "FIRST_PERSON");
        return config;
    }

    @Test void legacyFileKeepsKnownValuesAndGainsDefaults() {
        var config = legacyV1();
        assertFalse(OriClientConfig.SPEC.isCorrect(config), "v1 file lacks the R15 fields");
        OriClientConfig.SPEC.correct(config);
        assertTrue(OriClientConfig.SPEC.isCorrect(config));
        assertFalse(config.contains("keyDefaultsApplied"), "Obsolete key migration must be retired");
        assertEquals("FIRST_PERSON", String.valueOf(config.<Object>get("spiritCamera")));
        assertEquals(ClientConfigMigration.LEGACY, (int)config.<Integer>get("configVersion"));
        assertEquals("BALANCED", String.valueOf(config.<Object>get("visuals.quality")));
        assertEquals(true, config.get("visuals.advancedParticles"));
        // Public release: Spirit Glow is opt-in; the first-run hint and kept-pack state live in SpiritGlowState
        assertEquals(false, config.get("visuals.spiritGlow"));
        assertEquals(OriClientConfig.DEFAULT_SHAKE, (int)config.<Integer>get("feedback.screenShake"));
        assertEquals(OriClientConfig.DEFAULT_FLASH, (int)config.<Integer>get("feedback.screenFlash"));
    }

    @Test void emptyFileGetsPlanDefaults() {
        var config = CommentedConfig.inMemory();
        OriClientConfig.SPEC.correct(config);
        assertFalse(config.contains("keyDefaultsApplied"), "Fresh installs must not enable key migration");
        assertEquals("THIRD_PERSON_BACK", String.valueOf(config.<Object>get("spiritCamera")));
        assertEquals("BALANCED", String.valueOf(config.<Object>get("visuals.quality")));
        // Plan 10.1: shake light by default; it must not start at full strength.
        assertTrue(OriClientConfig.DEFAULT_SHAKE > 0 && OriClientConfig.DEFAULT_SHAKE <= 40);
        assertTrue(OriClientConfig.DEFAULT_FLASH > 0 && OriClientConfig.DEFAULT_FLASH < 100);
    }

    @Test void validUserChoicesSurviveCorrection() {
        var config = legacyV1();
        config.set("configVersion", 2);
        config.set("visuals.quality", "FLUENT");
        config.set("visuals.advancedParticles", false);
        config.set("feedback.screenShake", 0);
        config.set("feedback.screenFlash", 100);

        OriClientConfig.SPEC.correct(config);
        assertEquals("FLUENT", String.valueOf(config.<Object>get("visuals.quality")));
        assertEquals(false, config.get("visuals.advancedParticles"));
        assertEquals(0, (int)config.<Integer>get("feedback.screenShake"));
        assertEquals(100, (int)config.<Integer>get("feedback.screenFlash"));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 101, 5000})
    void outOfRangeStrengthIsCorrectedWithoutTouchingOtherValues(int bad) {
        var config = legacyV1();
        config.set("feedback.screenShake", bad);
        config.set("feedback.screenFlash", 7);
        OriClientConfig.SPEC.correct(config);
        int shake = config.<Integer>get("feedback.screenShake");
        assertTrue(shake >= 0 && shake <= 100, "corrected to " + shake);
        assertEquals(7, (int)config.<Integer>get("feedback.screenFlash"));
        assertEquals("FIRST_PERSON", String.valueOf(config.<Object>get("spiritCamera")));
    }

    @Test void unknownQualityAndCameraFallBackToDefaults() {
        var config = legacyV1();
        config.set("visuals.quality", "ULTRA");
        config.set("spiritCamera", "SPECTATOR");
        OriClientConfig.SPEC.correct(config);
        assertEquals("BALANCED", String.valueOf(config.<Object>get("visuals.quality")));
        assertEquals("THIRD_PERSON_BACK", String.valueOf(config.<Object>get("spiritCamera")));
        assertFalse(config.contains("keyDefaultsApplied"), "Obsolete key migration must be retired");
    }

    @Test void everyStoredCameraNameStillLoads() {
        for (var view : SpiritCameraView.values()) {
            var config = legacyV1();
            config.set("spiritCamera", view.name());
            OriClientConfig.SPEC.correct(config);
            assertEquals(view.name(), String.valueOf(config.<Object>get("spiritCamera")));
        }
    }

    @Test void cameraViewNamesMatchVanillaCameraTypes() {
        for (var view : SpiritCameraView.values()) assertEquals(view.name(), net.minecraft.client.CameraType.valueOf(view.name()).name());
        assertEquals(net.minecraft.client.CameraType.values().length, SpiritCameraView.values().length);
        assertEquals(SpiritCameraView.THIRD_PERSON_BACK, SpiritCameraView.parse("SPECTATOR"));
        assertEquals(SpiritCameraView.THIRD_PERSON_BACK, SpiritCameraView.parse(null));
    }

    @Test void migrationPlans() {
        var fromMissing = ClientConfigMigration.plan(ClientConfigMigration.LEGACY);
        assertTrue(fromMissing.changed());
        assertEquals(ClientConfigMigration.CURRENT, fromMissing.to());
        assertFalse(fromMissing.notes().isEmpty());
        var current = ClientConfigMigration.plan(ClientConfigMigration.CURRENT);
        assertFalse(current.changed());
        assertTrue(current.notes().isEmpty());
        var newer = ClientConfigMigration.plan(ClientConfigMigration.CURRENT + 3);
        assertFalse(newer.changed(), "never downgrade a file written by a newer build");
        assertEquals(1, newer.notes().size());
        assertEquals(ClientConfigMigration.CURRENT, ClientConfigMigration.plan(0).to());
    }

    @Test void unloadedSpecUsesDefaultsInsteadOfThrowing() {
        assertFalse(OriClientConfig.SPEC.isLoaded());
        assertEquals(OriClientConfig.Visuals.DEFAULTS, OriClientConfig.visuals());
        assertEquals(VisualQuality.BALANCED, OriClientConfig.Visuals.DEFAULTS.quality());
        assertTrue(OriClientConfig.Visuals.DEFAULTS.advancedParticles());
        assertTrue(OriClientConfig.companionHints());
    }

    @Test void companionHintDefaultMigratesAndExplicitOptOutSurvives() {
        var config = legacyV1();
        OriClientConfig.SPEC.correct(config);
        assertEquals(true, config.get("companionHints"));
        config.set("companionHints", false);
        OriClientConfig.SPEC.correct(config);
        assertEquals(false, config.get("companionHints"));
        assertFalse(OriClientConfig.HIDDEN_IN_MENU.contains("companionHints"));
    }
}
