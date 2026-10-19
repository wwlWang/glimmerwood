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

    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void firstExportKeepsTheR18Look(SpiritFxKind kind) throws Exception {
        // R30 moves the table into data unchanged; later visual passes update this expectation per kind.
        var runtime = SpiritFxDefinition.parse(kind, read("src/main/resources/assets/orimod/spirit_fx/" + SpiritFxDefinition.fileName(kind) + ".json"));
        assertEquals(SpiritFxDefinition.fallback(kind), runtime);
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
}
