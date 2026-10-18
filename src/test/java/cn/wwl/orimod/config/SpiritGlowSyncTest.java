package cn.wwl.orimod.config;

import org.junit.jupiter.api.Test;

import static cn.wwl.orimod.config.SpiritGlowSync.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class SpiritGlowSyncTest {
    private static final SpiritGlowSync.IrisState NONE_CHOSEN = new SpiritGlowSync.IrisState(null, true);
    private static final SpiritGlowSync.IrisState BLANK = new SpiritGlowSync.IrisState("", true);
    private static final SpiritGlowSync.IrisState OURS = new SpiritGlowSync.IrisState(SpiritGlowSync.PACK, true);
    private static final SpiritGlowSync.IrisState OURS_OFF = new SpiritGlowSync.IrisState(SpiritGlowSync.PACK, false);
    private static final SpiritGlowSync.IrisState USER = new SpiritGlowSync.IrisState("BSL.zip", true);
    private static final SpiritGlowSync.IrisState USER_OFF = new SpiritGlowSync.IrisState("BSL.zip", false);

    @Test void firstRunWithoutAPackSelectsSpiritGlow() {
        assertEquals(ENABLE, SpiritGlowSync.decide(false, true, null, NONE_CHOSEN));
        assertEquals(ENABLE, SpiritGlowSync.decide(false, true, null, BLANK));
    }
    @Test void firstRunKeepsAUserChosenPack() {
        assertEquals(SAVE_OFF, SpiritGlowSync.decide(false, true, null, USER));
        // a chosen pack with shaders switched off is still the player's choice
        assertEquals(SAVE_OFF, SpiritGlowSync.decide(false, true, null, USER_OFF));
    }
    @Test void firstRunWithToggleAlreadyOffDoesNothing() {
        assertEquals(NONE, SpiritGlowSync.decide(false, false, null, NONE_CHOSEN));
        assertEquals(NONE, SpiritGlowSync.decide(false, false, null, USER));
    }
    @Test void firstRunAlreadyOnSpiritGlowIsNoop() { assertEquals(NONE, SpiritGlowSync.decide(false, true, null, OURS)); }

    @Test void steadyStates() {
        assertEquals(NONE, SpiritGlowSync.decide(true, true, true, OURS));
        assertEquals(NONE, SpiritGlowSync.decide(true, false, false, USER));
        assertEquals(NONE, SpiritGlowSync.decide(true, false, null, USER));
    }
    @Test void settingsToggleIsApplied() {
        assertEquals(ENABLE, SpiritGlowSync.decide(true, true, false, USER));
        assertEquals(DISABLE, SpiritGlowSync.decide(true, false, true, OURS));
    }
    @Test void settingsToggleWinsOverSameTickIrisChange() {
        assertEquals(ENABLE, SpiritGlowSync.decide(true, true, false, USER_OFF));
    }
    @Test void irisChangesAreSavedNotReverted() {
        assertEquals(SAVE_OFF, SpiritGlowSync.decide(true, true, true, USER));
        assertEquals(SAVE_OFF, SpiritGlowSync.decide(true, true, true, OURS_OFF));
        assertEquals(SAVE_ON, SpiritGlowSync.decide(true, false, false, OURS));
    }
    @Test void startupAfterChangingPacksWhileClosedSyncsTheToggle() {
        assertEquals(SAVE_OFF, SpiritGlowSync.decide(true, true, null, USER));
        assertEquals(SAVE_ON, SpiritGlowSync.decide(true, false, null, OURS));
    }
    @Test void enablingRemembersOnlyAnotherPack() {
        assertEquals(USER, SpiritGlowSync.remember(USER));
        assertEquals(USER_OFF, SpiritGlowSync.remember(USER_OFF));
        assertNull(SpiritGlowSync.remember(NONE_CHOSEN));
        assertNull(SpiritGlowSync.remember(OURS_OFF));
    }
    @Test void disablingRestoresThePreviousPackOrTurnsShadersOff() {
        assertEquals(new SpiritGlowSync.Restore("BSL.zip", true), SpiritGlowSync.restore("BSL.zip", true));
        assertEquals(new SpiritGlowSync.Restore("BSL.zip", false), SpiritGlowSync.restore("BSL.zip", false));
        assertEquals(new SpiritGlowSync.Restore(null, false), SpiritGlowSync.restore("", true));
        assertEquals(new SpiritGlowSync.Restore(null, false), SpiritGlowSync.restore(SpiritGlowSync.PACK, true));
    }
    @Test void bundledPackIsPackagedWithLicenses() throws Exception {
        var zip = java.nio.file.Path.of(System.getProperty("orimod.spiritGlowZip", "build/generated/shaderpack/orimod_shaderpack/spirit_glow.zip"));
        assertTrue(java.nio.file.Files.isRegularFile(zip), "zipSpiritGlow output");
        try (var fs = java.nio.file.FileSystems.newFileSystem(zip)) {
            for (var name : new String[]{"LICENSE", "LICENSE-APACHE", "README.md", "shaders/shaders.properties", "shaders/img/luts/spirit.png"})
                assertTrue(java.nio.file.Files.exists(fs.getPath(name)), name);
            assertFalse(java.nio.file.Files.exists(fs.getPath("shaders/img/luts/mellow.png")));
            String license = java.nio.file.Files.readString(fs.getPath("LICENSE"));
            assertTrue(license.contains("Copyright (c) 2026 TheCMK"), "original MIT notice kept");
            assertTrue(license.indexOf("Spirit Glow") < license.indexOf("MIT License"), "our notice above the original");
        }
    }

    @Test void stateRoundTripsAndStartsOverWhenMissingOrBroken(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var file = dir.resolve(SpiritGlowState.FILE);
        assertEquals(SpiritGlowState.INITIAL, SpiritGlowState.load(file));
        var state = SpiritGlowState.INITIAL.withSetupDone().withPrevious(USER_OFF);
        state.save(file);
        assertEquals(new SpiritGlowState(true, "BSL.zip", false), SpiritGlowState.load(file));
        new SpiritGlowState(true, "Pack = with: odd\\chars \u5149.zip", true).save(file);
        assertEquals("Pack = with: odd\\chars \u5149.zip", SpiritGlowState.load(file).previousPack());
        java.nio.file.Files.writeString(file, "previousPack=\\u12");
        assertEquals(SpiritGlowState.INITIAL, SpiritGlowState.load(file), "malformed escape");
        try (var files = java.nio.file.Files.list(dir)) { assertEquals(1, files.count(), "no temp files left"); }
    }
}
