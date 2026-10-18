package cn.wwl.orimod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpiritGlowProfileTest {
    @Test void tiersMapToTheThreeProfiles() {
        assertEquals("FAST", SpiritGlowProfile.profile(VisualQuality.FLUENT));
        assertEquals("FANCY", SpiritGlowProfile.profile(VisualQuality.BALANCED));
        assertEquals("FABULOUS", SpiritGlowProfile.profile(VisualQuality.DETAILED));
        for (var quality : VisualQuality.values())
            assertTrue(SpiritGlowProfile.MANAGED.containsAll(SpiritGlowProfile.overrides(quality).keySet()), quality.name());
    }

    @Test void freshFileGetsTheTierAndBalancedWritesNothing() {
        var fluent = SpiritGlowProfile.plan(Map.of(), Map.of(), VisualQuality.FLUENT);
        assertTrue(fluent.changed());
        assertEquals("1024", fluent.options().get("SHADOW_MAP_RESOLUTION"));
        assertEquals("false", fluent.options().get("GODRAYS"));
        assertEquals(SpiritGlowProfile.MANAGED.size(), fluent.owned().size());
        var balanced = SpiritGlowProfile.plan(Map.of(), Map.of(), VisualQuality.BALANCED);
        assertFalse(balanced.changed());
        assertTrue(balanced.options().isEmpty());
    }

    @Test void switchingTiersReplacesOnlyOurValuesAndKeepsOtherOptions() {
        var start = Map.of("SPIRIT_SHADOW_HUE", "2");
        var fluent = SpiritGlowProfile.plan(start, Map.of(), VisualQuality.FLUENT);
        var detailed = SpiritGlowProfile.plan(fluent.options(), fluent.owned(), VisualQuality.DETAILED);
        assertTrue(detailed.changed());
        assertEquals("2", detailed.options().get("SPIRIT_SHADOW_HUE"));
        assertEquals("true", detailed.options().get("SSAO"));
        assertNull(detailed.options().get("SHADOW_MAP_RESOLUTION"), "back to the pack default");
        assertNull(detailed.options().get("GODRAYS"), "back to the pack default");
        var balanced = SpiritGlowProfile.plan(detailed.options(), detailed.owned(), VisualQuality.BALANCED);
        assertEquals(Map.of("SPIRIT_SHADOW_HUE", "2"), balanced.options());
        assertFalse(SpiritGlowProfile.plan(balanced.options(), balanced.owned(), VisualQuality.BALANCED).changed());
    }

    @Test void optionsThePlayerChangedInIrisStayTheirs() {
        var fluent = SpiritGlowProfile.plan(Map.of(), Map.of(), VisualQuality.FLUENT);
        var edited = new java.util.TreeMap<>(fluent.options());
        edited.put("GODRAYS", "true");          // player turned god rays back on
        edited.put("SSAO", "true");             // player set an option we left at default
        var detailed = SpiritGlowProfile.plan(edited, fluent.owned(), VisualQuality.DETAILED);
        assertEquals("true", detailed.options().get("GODRAYS"));
        assertFalse(detailed.owned().containsKey("GODRAYS"));
        assertFalse(detailed.owned().containsKey("SSAO"));
        var fast = SpiritGlowProfile.plan(detailed.options(), detailed.owned(), VisualQuality.FLUENT);
        assertEquals("true", fast.options().get("GODRAYS"), "still theirs on the next switch");
        assertEquals("true", fast.options().get("SSAO"));
    }

    @Test void valuesSetBeforeTheFirstRunAreNotTaken() {
        var plan = SpiritGlowProfile.plan(Map.of("SHADOW_MAP_RESOLUTION", "4096"), Map.of(), VisualQuality.FLUENT);
        assertEquals("4096", plan.options().get("SHADOW_MAP_RESOLUTION"));
        assertFalse(plan.owned().containsKey("SHADOW_MAP_RESOLUTION"));
    }

    @Test void anOptionBackAtItsDefaultIsManagedAgain() {
        var detailed = SpiritGlowProfile.plan(Map.of(), Map.of(), VisualQuality.DETAILED);
        var dropped = new java.util.TreeMap<>(detailed.options());
        dropped.remove("IMAGE_SHARPENING");     // Iris rewrote the file without it (or the player reset it)
        var again = SpiritGlowProfile.plan(dropped, detailed.owned(), VisualQuality.DETAILED);
        assertEquals("true", again.options().get("IMAGE_SHARPENING"));
        assertTrue(again.changed());
    }

    @Test void filesRoundTrip(@TempDir Path dir) throws Exception {
        var file = dir.resolve("Spirit Glow.zip.txt");
        assertTrue(SpiritGlowProfile.read(file).isEmpty());
        var plan = SpiritGlowProfile.plan(Map.of(), Map.of(), VisualQuality.DETAILED);
        SpiritGlowProfile.write(file, plan.options(), "test");
        assertEquals(plan.options(), SpiritGlowProfile.read(file));
        var state = dir.resolve(SpiritGlowProfile.FILE);
        SpiritGlowProfile.write(state, plan.owned(), "test");
        assertEquals(plan.owned(), Map.copyOf(SpiritGlowProfile.read(state)));
    }
}
