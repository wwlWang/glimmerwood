package cn.wwl.orimod.client.render;

import com.google.gson.*;
import java.io.InputStreamReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class BuildingItemModelTest {
    static JsonObject read(String model) throws Exception {
        try(var stream=BuildingItemModelTest.class.getResourceAsStream("/assets/orimod/models/"+model+".json")) {
            assertNotNull(stream,model);return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        }
    }
    @ParameterizedTest @ValueSource(strings={"glades_well","forest_spring_shrine","creek_ruins_shrine","cave_altar_shrine","gloom_altar","storm_altar","map_stone","keystone_door","great_tree_heart","ginso_water_heart"})
    void architectureUsesItsWorldAssemblyInEveryItemContext(String item) throws Exception {
        var model=read("item/"+item);
        assertEquals("minecraft:builtin/entity",model.get("parent").getAsString(),"Item must select the shared architecture renderer");
        for(String context:new String[]{"gui","ground","fixed","firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand"})
            assertTrue(model.getAsJsonObject("display").has(context),item+" lacks "+context);
    }
    @ParameterizedTest @ValueSource(strings={"ginso_wood","great_spirit_root"})
    void fullCubesInheritBlockDisplayRatherThanLookingLikeFlatSquares(String item) throws Exception {
        var model=read("item/"+item);
        for(int i=0;i<8;i++) {
            assertTrue(model.has("parent"),item+" has no block display ancestor");
            String parent=model.get("parent").getAsString();
            if(parent.startsWith("minecraft:block/"))return;
            assertTrue(parent.startsWith("orimod:"),parent);model=read(parent.substring(7));
        }
        fail("Cyclic model ancestry: "+item);
    }
}
