package cn.wwl.orimod.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Lints the Spirit Glow pack source so a tuning edit cannot leave an option outside its menu values. */
class SpiritGlowPackTest {
    private static final Path PACK = Path.of(System.getProperty("orimod.spiritGlowSource", "shaderpacks/spirit-glow"));
    private static final Pattern OPTION = Pattern.compile("^\\s*(?:const\\s+\\w+\\s+(\\w+)\\s*=\\s*([^;]+);|#define\\s+(\\w+)\\s+(\\S+))\\s*//\\s*\\[([^\\]]*)\\]", Pattern.MULTILINE);

    private static String read(String path) throws Exception { return Files.readString(PACK.resolve(path)); }

    @Test void everyDefaultIsOneOfItsMenuValues() throws Exception {
        var matcher = OPTION.matcher(read("shaders/lib/settings.glsl"));
        int checked = 0;
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String value = (matcher.group(2) != null ? matcher.group(2) : matcher.group(4)).trim();
            var values = java.util.Arrays.asList(matcher.group(5).trim().split("\\s+"));
            assertTrue(values.contains(value), name + " = " + value + " not in " + values);
            checked++;
        }
        assertTrue(checked > 150, "parsed " + checked + " options");
    }

    @Test void spiritSchemeIsTheDefaultAndEverySchemeHasColoursAndNames() throws Exception {
        var settings = read("shaders/lib/settings.glsl");
        var scheme = Pattern.compile("#define COLOR_SCHEME (\\d+) // \\[([^\\]]*)\\]").matcher(settings);
        assertTrue(scheme.find());
        assertEquals("6", scheme.group(1), "Spirit palette is the default");
        var colors = read("shaders/lib/colors.glsl");
        var english = read("shaders/lang/en_US.lang");
        var chinese = read("shaders/lang/zh_CN.lang");
        for (var value : scheme.group(2).trim().split("\\s+")) {
            assertTrue(colors.contains("COLOR_SCHEME == " + value), "colours for scheme " + value);
            assertTrue(english.contains("value.COLOR_SCHEME." + value + "="), "en name " + value);
            assertTrue(chinese.contains("value.COLOR_SCHEME." + value + "="), "zh name " + value);
        }
        assertTrue(settings.contains("\n#define LUT\n") && settings.contains("#define LUT_FILE 9 "), "Spirit LUT on by default");
    }

    @Test void packCarriesNoUpstreamBrandingBeyondTheCredit() throws Exception {
        try (var files = Files.walk(PACK.resolve("shaders"))) {
            for (var file : (Iterable<Path>)files.filter(Files::isRegularFile)::iterator) {
                assertFalse(file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("mellow"), file.toString());
                String name = file.getFileName().toString();
                if (!(name.endsWith(".lang") || name.endsWith(".properties") || name.endsWith(".glsl") || name.endsWith(".fsh") || name.endsWith(".vsh"))) continue;
                for (var line : Files.readAllLines(file)) {
                    if (line.toLowerCase(java.util.Locale.ROOT).contains("mellow"))
                        assertTrue(line.startsWith("option.INFO="), "only the credit line may name the original: " + file + ": " + line);
                }
            }
        }
    }

    @Test void spiritStyleOptionsAreOnTheirScreenAndNamedInBothLanguages() throws Exception {
        var settings = read("shaders/lib/settings.glsl");
        var properties = read("shaders/shaders.properties");
        var english = read("shaders/lang/en_US.lang");
        var chinese = read("shaders/lang/zh_CN.lang");
        var screen = Pattern.compile("^screen\\.SPIRIT_STYLE_SETTINGS=(.*)$", Pattern.MULTILINE).matcher(properties);
        assertTrue(screen.find(), "Spirit Style screen");
        var onScreen = java.util.Arrays.asList(screen.group(1).trim().split("\\s+"));
        var defined = Pattern.compile("#define (SPIRIT_\\w+)").matcher(settings);
        int count = 0;
        while (defined.find()) {
            String name = defined.group(1);
            if (name.equals("SPIRIT_STYLE")) assertTrue(properties.contains("SPIRIT_STYLE [SPIRIT_STYLE_SETTINGS]"), "main screen entry");
            else assertTrue(onScreen.contains(name), name + " on the Spirit Style screen");
            assertTrue(english.contains("option." + name + "="), "en " + name);
            assertTrue(chinese.contains("option." + name + "="), "zh " + name);
            count++;
        }
        assertTrue(count >= 11, "parsed " + count + " Spirit Style options");
        assertTrue(settings.contains("\n#define SPIRIT_STYLE\n") && settings.contains("\n#define SPIRIT_DEPTH_HAZE\n"), "style and haze on by default");
        assertTrue(settings.contains("\n// #define SPIRIT_PAINTERLY\n"), "painterly smoothing stays opt-in");
        assertTrue(settings.contains("\n#define GODRAYS\n"), "god rays on by default");
    }
}
