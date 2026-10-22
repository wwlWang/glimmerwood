package cn.wwl.orimod.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritSelfLightTest {
    @Test void pawsKeepTheWorldBodyLightFloorWithoutChangingSkyLight() {
        for (int sky = 0; sky <= 15; sky++) for (int block = 0; block <= 15; block++)
            assertEquals((sky << 20) | 240, SpiritSelfLight.packed((sky << 20) | (block << 4)));
    }

    @Test void bodyMaterialTagKeepsVanillaLightLevelsAndIsAbsentWithoutOurPack() {
        for (int sky = 0; sky <= 15; sky++) for (int block = 0; block <= 15; block++) {
            int ambient = sky << 20 | block << 4;
            assertEquals(SpiritSelfLight.packed(ambient), SpiritSelfLight.bodyLight(ambient, false));
            int marked = SpiritSelfLight.bodyLight(ambient, true);
            assertEquals(sky, marked >> 20 & 15);
            assertEquals(15, marked >> 4 & 15, "tag still addresses the full-bright lightmap cell");
            assertEquals(1, marked ^ SpiritSelfLight.packed(ambient), "only the reserved low bit changes");
        }
        assertEquals(255, SpiritSelfLight.handAlpha(false), "ordinary path keeps its exact original vertex colour");
    }

    @Test void shaderAndVertexWritersAgreeOnTheMaterialTags() throws Exception {
        var path = java.nio.file.Path.of(System.getProperty("orimod.spiritGlowSource", "shaderpacks/spirit-glow"),
                "shaders/global/lighting/spirit_self_light.glsl");
        String source = java.nio.file.Files.readString(path);
        var hand = java.util.regex.Pattern.compile("ORIMOD_HAND_ALPHA = (\\d+)\\.0 / 255\\.0").matcher(source);
        var body = java.util.regex.Pattern.compile("ORIMOD_BODY_LIGHT = (\\d+)\\.0").matcher(source);
        assertTrue(hand.find()); assertTrue(body.find());
        assertEquals(SpiritSelfLight.handAlpha(true), Integer.parseInt(hand.group(1)));
        assertEquals(SpiritSelfLight.bodyLight(0, true), Integer.parseInt(body.group(1)));
        float encoded = SpiritSelfLight.handAlpha(true) / 255f;
        assertTrue(Math.abs(encoded - 254f / 255f) < .25f / 255f);
        assertFalse(Math.abs(1f - encoded) < .25f / 255f, "opaque vanilla vertices must never select Ori's material");
    }
}
