package cn.wwl.orimod.assets;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** R76: every block/item model draws only original orimod textures, and each referenced texture has 16x16 frames (R106 cores animate). */
class OriginalTexturesTest {
    private static final Path ASSETS = Path.of(System.getProperty("orimod.projectDir", ".")).resolve("src/main/resources/assets/orimod");

    @Test void modelsUseOnlyOriginalTexturesThatExist() throws Exception {
        int checked = 0;
        try (var files = Files.walk(ASSETS.resolve("models"))) {
            for (var f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                var json = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if (!json.has("textures")) continue;
                for (var e : json.getAsJsonObject("textures").entrySet()) {
                    String ref = e.getValue().getAsString();
                    if (ref.startsWith("#")) continue;
                    assertTrue(ref.startsWith("orimod:"), f + " borrows " + ref);
                    var png = ASSETS.resolve("textures/" + ref.substring(7) + ".png");
                    assertTrue(Files.exists(png), f + " -> missing " + png);
                    var img = javax.imageio.ImageIO.read(png.toFile());
                    assertEquals(16, img.getWidth(), png.toString());
                    if (ref.matches("orimod:block/gloom_core_[0-4]")) {
                        assertEquals(16 * 12, img.getHeight(), png.toString());
                        var meta = JsonParser.parseString(Files.readString(Path.of(png + ".mcmeta"))).getAsJsonObject().getAsJsonObject("animation");
                        assertEquals(4, meta.get("frametime").getAsInt()); assertTrue(meta.get("interpolate").getAsBoolean());
                    } else assertEquals(16, img.getHeight(), png.toString());
                    checked++;
                }
            }
        }
        assertTrue(checked >= 50, "checked " + checked);
    }
}
