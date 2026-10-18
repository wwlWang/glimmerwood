package cn.wwl.orimod.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Spirit Glow bookkeeping kept outside orimod-client.toml: whether the first-run choice was made and
 * which shader pack to restore. A separate file, so a reload of the settings file (after the settings
 * screen saves it) cannot overwrite a pack remembered in the same moment.
 *
 * @param previousPack pack restored when Spirit Glow is turned off; empty turns shaders off instead
 */
public record SpiritGlowState(boolean setupDone, String previousPack, boolean previousEnabled) {
    public static final String FILE = "orimod-spirit-glow.properties";
    public static final SpiritGlowState INITIAL = new SpiritGlowState(false, "", true);

    public SpiritGlowState {
        previousPack = previousPack == null ? "" : previousPack;
    }

    public SpiritGlowState withSetupDone() { return new SpiritGlowState(true, previousPack, previousEnabled); }

    public SpiritGlowState withPrevious(SpiritGlowSync.IrisState previous) {
        return new SpiritGlowState(setupDone, previous.pack(), previous.enabled());
    }

    /** Missing or unreadable files start over with {@link #INITIAL}, which never replaces a user-chosen pack. */
    public static SpiritGlowState load(Path file) {
        if (!Files.isRegularFile(file)) return INITIAL;
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException error) {
            return INITIAL;
        }
        return new SpiritGlowState(Boolean.parseBoolean(properties.getProperty("setupDone", "false")),
                properties.getProperty("previousPack", ""),
                Boolean.parseBoolean(properties.getProperty("previousEnabled", "true")));
    }

    public void save(Path file) throws IOException {
        var properties = new Properties();
        properties.setProperty("setupDone", Boolean.toString(setupDone));
        properties.setProperty("previousPack", previousPack);
        properties.setProperty("previousEnabled", Boolean.toString(previousEnabled));
        Files.createDirectories(file.toAbsolutePath().getParent());
        var temp = Files.createTempFile(file.toAbsolutePath().getParent(), "orimod-spirit-glow", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, java.nio.charset.StandardCharsets.UTF_8)) {
                properties.store(writer, "oriMod Spirit Glow shader state; the on/off toggle is visuals.spiritGlow in orimod-client.toml");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
