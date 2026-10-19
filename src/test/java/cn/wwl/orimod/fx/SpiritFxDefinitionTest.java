package cn.wwl.orimod.fx;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SpiritFxDefinitionTest {
    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.projectDir", ".")).resolve(path))).getAsJsonObject();
    }

    private static JsonObject withoutNotes(JsonObject json) {
        var copy = json.deepCopy(); copy.remove("notes");
        copy.getAsJsonArray("layers").forEach(layer -> layer.getAsJsonObject().remove("notes"));
        return copy;
    }

    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void everyKindShipsAnExportedDefinitionMatchingItsSource(SpiritFxKind kind) throws Exception {
        String name = SpiritFxDefinition.fileName(kind);
        var runtime = SpiritFxDefinition.parse(kind, read("src/main/resources/assets/orimod/spirit_fx/" + name + ".json"));
        var source = SpiritFxDefinition.parse(kind, withoutNotes(read("art/source/effects/spirit/" + name + ".json")));
        assertEquals(source, runtime, "re-export art/source/effects/spirit with scripts/export-spirit-fx.py");
    }

    private static final java.util.Set<String> VANILLA = java.util.Set.of("minecraft:crit", "minecraft:cloud", "minecraft:end_rod", "minecraft:glow",
            "minecraft:firework", "minecraft:electric_spark");

    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void formalLooksUseSpiritLightFirstAndKnownParticles(SpiritFxKind kind) throws Exception {
        // R30 step 2: every effect leads with the mod's own spirit light (essential particles land in layer 1),
        // and every particle is either a vanilla simple particle or one this mod ships sprites for.
        var runtime = SpiritFxDefinition.parse(kind, read("src/main/resources/assets/orimod/spirit_fx/" + SpiritFxDefinition.fileName(kind) + ".json"));
        assertTrue(runtime.layers().get(0).particle().startsWith("orimod:spirit_"), kind + " layer 1");
        for (var layer : runtime.layers()) {
            String id = layer.particle();
            if (id.startsWith("orimod:")) {
                String name = id.substring(7);
                var sprites = read("src/main/resources/assets/orimod/particles/" + name + ".json").getAsJsonArray("textures");
                assertFalse(sprites.isEmpty());
                for (var sprite : sprites) assertTrue(Files.exists(Path.of(System.getProperty("orimod.projectDir", "."))
                        .resolve("src/main/resources/assets/orimod/textures/particle/" + sprite.getAsString().substring(7) + ".png")), sprite.toString());
            } else assertTrue(VANILLA.contains(id), id);
        }
    }

    private static JsonObject valid() {
        return JsonParser.parseString("{\"format\":1,\"kind\":\"dash\",\"layers\":[{\"particle\":\"minecraft:cloud\",\"share\":0.25,\"spread\":[0.1,0.1,0.1],\"speed\":0.01},"
                + "{\"particle\":\"orimod:spirit_mote\",\"share\":0.75,\"spread\":[0.3,0.3,0.3],\"speed\":0.02}]}").getAsJsonObject();
    }

    @Test void layersSplitTheBudgetInOrder() {
        var d = SpiritFxDefinition.parse(SpiritFxKind.DASH, valid());
        assertEquals("minecraft:cloud", d.layerFor(0, 8).particle());
        assertEquals("minecraft:cloud", d.layerFor(1, 8).particle());
        assertEquals("orimod:spirit_mote", d.layerFor(2, 8).particle());
        assertEquals("orimod:spirit_mote", d.layerFor(7, 8).particle());
    }

    @Test void brokenDefinitionsAreRejected() {
        var wrongKind = valid(); wrongKind.addProperty("kind", "bash");
        assertThrows(IllegalArgumentException.class, () -> SpiritFxDefinition.parse(SpiritFxKind.DASH, wrongKind));
        var shares = valid(); shares.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("share", 0.5);
        assertThrows(IllegalArgumentException.class, () -> SpiritFxDefinition.parse(SpiritFxKind.DASH, shares));
        var id = valid(); id.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("particle", "Not An Id");
        assertThrows(IllegalArgumentException.class, () -> SpiritFxDefinition.parse(SpiritFxKind.DASH, id));
        var format = valid(); format.addProperty("format", 2);
        assertThrows(IllegalArgumentException.class, () -> SpiritFxDefinition.parse(SpiritFxKind.DASH, format));
    }

    @Test void shapeFieldsParseWithDefaults() {
        var json = valid(); var l = json.getAsJsonArray("layers").get(1).getAsJsonObject();
        l.addProperty("shape", "ring"); l.addProperty("radius", 0.5); l.addProperty("color", "#ffd27a"); l.addProperty("size", 0.4); l.addProperty("lifetime", 6);
        var d = SpiritFxDefinition.parse(SpiritFxKind.DASH, json);
        assertEquals("burst", d.layers().get(0).shape());
        var ring = d.layers().get(1);
        assertEquals("ring", ring.shape()); assertEquals(0xFFD27A, ring.rgb()); assertEquals(0.4f, ring.size(), 1e-6); assertEquals(6, ring.lifetime());
    }

    @Test void badShapeFieldsAreRejected() {
        for (var bad : new String[][] {{"shape", "spiral"}, {"color", "#FFD27A"}, {"color", "red"}}) {
            var json = valid(); json.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty(bad[0], bad[1]);
            assertThrows(IllegalArgumentException.class, () -> SpiritFxDefinition.parse(SpiritFxKind.DASH, json), bad[0] + "=" + bad[1]);
        }
        var big = valid(); big.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("radius", 9);
        assertThrows(IllegalArgumentException.class, () -> SpiritFxDefinition.parse(SpiritFxKind.DASH, big));
    }

    @Test void firstLayerAlwaysGetsTheFirstParticle() {
        // share 0.25 of a 1-particle budget would round to 0; the must-show particle still lands in layer 1
        var d = SpiritFxDefinition.parse(SpiritFxKind.DASH, valid());
        assertEquals("minecraft:cloud", d.layerFor(0, 1).particle());
        var slot = d.slot(3, 8);
        assertEquals(1, slot.j()); assertEquals(6, slot.n());
    }
}
