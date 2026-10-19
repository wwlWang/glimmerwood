package cn.wwl.orimod.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** R42: Gloom Spitter tuning and its natural spawn data. */
class GloomSpitterTest {
    @Test void orbIsSlowEnoughToBash() {
        assertTrue(GloomShot.SPEED < 1, "orb must be slower than a drawn arrow so it can be caught");
        assertTrue(GloomShot.DAMAGE <= 4);
        assertTrue(GloomSpitter.ATTACK_INTERVAL >= 40);
    }

    @Test void spawnsInTheOverworldOnly() throws Exception {
        var json = JsonParser.parseString(new String(GloomSpitterTest.class.getResourceAsStream(
                "/data/orimod/neoforge/biome_modifier/gloom_spitter.json").readAllBytes())).getAsJsonObject();
        assertEquals("neoforge:add_spawns", json.get("type").getAsString());
        assertEquals("#minecraft:is_overworld", json.get("biomes").getAsString());
        assertEquals("orimod:gloom_spitter", json.getAsJsonObject("spawners").get("type").getAsString());
    }
}
