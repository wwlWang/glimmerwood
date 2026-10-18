package cn.wwl.orimod.client.render;

import cn.wwl.orimod.config.SpiritGlowSync;
import net.irisshaders.iris.Iris;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Iris calls for {@link SpiritGlow}. Loaded only when Iris is installed. */
final class SpiritGlowIris {
    private SpiritGlowIris() {}

    static SpiritGlowSync.IrisState state() {
        var config = Iris.getIrisConfig();
        return new SpiritGlowSync.IrisState(config.getShaderPackName().orElse(null), config.areShadersEnabled());
    }

    /** Copies the bundled pack into the shaderpacks folder; only our own file is replaced, and only when it differs. */
    static boolean install() throws IOException {
        byte[] bundled;
        try (var in = SpiritGlow.class.getResourceAsStream(SpiritGlow.RESOURCE)) {
            if (in == null) {
                cn.wwl.orimod.OriMod.LOGGER.warn("Spirit Glow: bundled shader pack {} is missing from the mod", SpiritGlow.RESOURCE);
                return false;
            }
            bundled = in.readAllBytes();
        }
        var dir = Iris.getShaderpacksDirectory();
        var target = dir.resolve(SpiritGlowSync.PACK);
        if (Files.isRegularFile(target) && java.util.Arrays.equals(Files.readAllBytes(target), bundled)) return true;
        Files.createDirectories(dir);
        var temp = Files.createTempFile(dir, "spirit_glow", ".tmp");
        try {
            Files.write(temp, bundled);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        cn.wwl.orimod.OriMod.LOGGER.info("Spirit Glow: installed {} ({} bytes)", target, bundled.length);
        return true;
    }

    /** Selects {@code pack} (keeps the current name when {@code null}), switches shaders and reloads Iris. */
    static void apply(String pack, boolean enabled) throws IOException {
        var config = Iris.getIrisConfig();
        if (pack != null) config.setShaderPackName(pack);
        config.setShadersEnabled(enabled);
        config.save();
        Iris.reload();
        invalidatePhoton();
    }

    /** Iris keeps a pack's option values next to it as {@code <pack>.txt}. */
    static java.nio.file.Path optionsFile() {
        return Iris.getShaderpacksDirectory().resolve(SpiritGlowSync.PACK + ".txt");
    }

    /** Rebuilds the active pipeline so changed pack options take effect. */
    static void reload() throws IOException {
        Iris.reload();
        invalidatePhoton();
    }

    static boolean exists(String pack) {
        try {
            return Files.exists(Iris.getShaderpacksDirectory().resolve(pack));
        } catch (java.nio.file.InvalidPathException invalid) {
            return false;
        }
    }

    static String storedError() {
        return Iris.getStoredError().map(Throwable::toString).orElse(null);
    }

    /** Photon caches Iris state for its effects; optional, so looked up reflectively. */
    private static void invalidatePhoton() {
        try {
            Class.forName("com.lowdragmc.photon.client.compat.iris.IrisCompat").getMethod("invalidate").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }
}
