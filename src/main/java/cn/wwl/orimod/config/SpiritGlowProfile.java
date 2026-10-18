package cn.wwl.orimod.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/**
 * PERF04: maps the mod's effect quality tier to a Spirit Glow shader profile (FLUENT → FAST,
 * BALANCED → FANCY, DETAILED → FABULOUS) by writing the pack's Iris option file
 * ({@code shaderpacks/Spirit Glow.zip.txt}).
 *
 * <p>Only the {@link #MANAGED} options are touched, and only while they still hold the value this
 * mod wrote last; an option the player changed in the Iris pack screen stays theirs. Like Iris, the
 * file lists only values that differ from the pack defaults, so an absent key means "default".
 */
public final class SpiritGlowProfile {
    public static final String FILE = "orimod-spirit-glow-profile.properties";
    /** Pack options set per tier; all other options belong to the player. */
    public static final List<String> MANAGED = List.of("BLOOM", "CLOUD_STYLE", "WATER_NORMALS", "REFLECTIONS",
            "GODRAYS", "GODRAYS_QUALITY", "DYNAMIC_SHADOWS", "SHADOW_MAP_RESOLUTION", "SSAO", "TAA_MODE", "SMAA",
            "USE_LQ_DEPTH", "IMAGE_SHARPENING");

    private SpiritGlowProfile() {}

    /** Name of the matching {@code profile.*} line in the pack's shaders.properties. */
    public static String profile(VisualQuality quality) {
        return switch (quality) {
            case FLUENT -> "FAST";
            case BALANCED -> "FANCY";
            case DETAILED -> "FABULOUS";
        };
    }

    /** Managed options that differ from the pack defaults for this tier; BALANCED is the pack default. */
    public static Map<String, String> overrides(VisualQuality quality) {
        return switch (quality) {
            // Bloom stays on: the soft glow is part of the look and cheap next to shadows, reflections and god rays.
            case FLUENT -> Map.of("CLOUD_STYLE", "0", "WATER_NORMALS", "false", "REFLECTIONS", "0", "GODRAYS", "false",
                    "DYNAMIC_SHADOWS", "false", "SHADOW_MAP_RESOLUTION", "1024");
            case BALANCED -> Map.of();
            case DETAILED -> Map.of("REFLECTIONS", "2", "GODRAYS_QUALITY", "6", "SSAO", "true", "TAA_MODE", "1",
                    "SMAA", "true", "USE_LQ_DEPTH", "true", "IMAGE_SHARPENING", "true");
        };
    }

    /**
     * @param options new content of the Iris option file
     * @param owned   what this mod now holds per managed option ("" = left at the pack default); options the
     *                player changed are missing
     * @param changed whether {@code options} differs from the current file
     */
    public record Plan(Map<String, String> options, Map<String, String> owned, boolean changed) {}

    /**
     * @param current options now in the Iris file
     * @param owned   the {@link Plan#owned} saved last time; empty when this mod never wrote the file
     */
    public static Plan plan(Map<String, String> current, Map<String, String> owned, VisualQuality quality) {
        var target = overrides(quality);
        var options = new TreeMap<>(current);
        var nowOwned = new LinkedHashMap<String, String>();
        for (var key : MANAGED) {
            String expected = owned.get(key);
            if (expected != null && expected.isEmpty()) expected = null;
            // Ours while it still holds what we wrote (or the default, which we may always take back).
            if (!Objects.equals(current.get(key), expected)) continue;
            String value = target.get(key);
            if (value == null) options.remove(key); else options.put(key, value);
            nowOwned.put(key, value == null ? "" : value);
        }
        return new Plan(options, nowOwned, !options.equals(new TreeMap<>(current)));
    }

    public static Map<String, String> read(Path file) {
        var values = new TreeMap<String, String>();
        if (!Files.isRegularFile(file)) return values;
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException error) {
            return values;
        }
        for (var name : properties.stringPropertyNames()) values.put(name, properties.getProperty(name));
        return values;
    }

    public static void write(Path file, Map<String, String> values, String comment) throws IOException {
        var properties = new Properties();
        properties.putAll(values);
        Files.createDirectories(file.toAbsolutePath().getParent());
        var temp = Files.createTempFile(file.toAbsolutePath().getParent(), "orimod-profile", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, comment);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
