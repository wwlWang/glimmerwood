package cn.wwl.orimod.client.render;

import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Conservative model bounds under the real vanilla hand origin and spirit's shared view offset. */
class BuildingItemFramingTest {
    @ParameterizedTest @ValueSource(strings={"glades_well","forest_spring_shrine","creek_ruins_shrine","cave_altar_shrine","gloom_altar","storm_altar","map_stone","keystone_door","great_tree_heart","ginso_water_heart"})
    void entireBuildingFitsBothHandsInBothForms(String name) throws Exception {
        var display=BuildingItemModelTest.read("item/"+name).getAsJsonObject("display");
        float h=name.equals("great_tree_heart")?3.65f:name.equals("keystone_door")?3:name.equals("map_stone")?2.25f:2.8f;
        float w=name.equals("map_stone")?1.4f:3;
        float fit=.88f/Math.max(h,w),halfW=w*fit/2,halfH=h*fit/2;
        for(boolean spirit:new boolean[]{false,true})for(boolean left:new boolean[]{false,true}) {
            var d=display.getAsJsonObject("firstperson_"+(left?"lefthand":"righthand"));
            float sign=left?-1:1;
            var m=new Matrix4f().translation(sign*(.56f-(spirit?.10f:0)),-.52f+(spirit?.22f:0),-.72f-(spirit?.20f:0));
            m.translate(sign*value(d,"translation",0)/16,value(d,"translation",1)/16,value(d,"translation",2)/16);
            m.rotateXYZ(rad(value(d,"rotation",0)),rad(sign*value(d,"rotation",1)),rad(sign*value(d,"rotation",2)));
            m.scale(value(d,"scale",0),value(d,"scale",1),value(d,"scale",2));
            float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
            for(float x:new float[]{-halfW,halfW})for(float y:new float[]{-halfH,halfH})for(float z:new float[]{-halfW,halfW}) {
                var p=m.transformPosition(new Vector3f(x,y,z));
                float screenY=p.y/(-p.z*(float)Math.tan(Math.toRadians(35)));
                float screenX=p.x/(-p.z*(float)Math.tan(Math.toRadians(35))*1920/1015);
                String context=name+" spirit="+spirit+" left="+left+" projected="+screenX+","+screenY;
                assertTrue(p.z<-.2f,context+" crosses near plane");
                assertTrue(Math.abs(screenY)<.95f && Math.abs(screenX)<.95f,context+" is clipped");
                minX=Math.min(minX,screenX);maxX=Math.max(maxX,screenX);
            }
            assertTrue((maxX-minX)/2<=.28f,name+" occupies more than 28% of viewport width");
        }
    }
    @ParameterizedTest @ValueSource(strings={"glades_well","forest_spring_shrine","creek_ruins_shrine","cave_altar_shrine","gloom_altar","storm_altar","map_stone","keystone_door","great_tree_heart","ginso_water_heart"})
    void thirdPersonDoesNotExceedVanillaBlockScale(String name)throws Exception {
        var display=BuildingItemModelTest.read("item/"+name).getAsJsonObject("display");
        for(String hand:new String[]{"righthand","lefthand"})for(int i=0;i<3;i++)
            assertTrue(value(display.getAsJsonObject("thirdperson_"+hand),"scale",i)<=.375f,name+" is larger than the vanilla block hand scale");
    }
    @ParameterizedTest @ValueSource(strings={"ginso_wood","great_spirit_root"})
    void plainCubesKeepVanillaDisplayWithoutLocalOverrides(String name)throws Exception {
        var model=BuildingItemModelTest.read("item/"+name);
        assertFalse(model.has("display"));
        var cube=BuildingItemModelTest.read(model.get("parent").getAsString().substring("orimod:".length()));
        assertEquals("minecraft:block/block",cube.get("parent").getAsString());
        assertFalse(cube.has("display"),"Plain cubes must use the same hand scale/rotation as oak_log");
        var elements=cube.getAsJsonArray("elements");assertEquals(1,elements.size());
        var box=elements.get(0).getAsJsonObject();
        for(int axis=0;axis<3;axis++) {
            assertEquals(0,box.getAsJsonArray("from").get(axis).getAsInt());
            assertEquals(16,box.getAsJsonArray("to").get(axis).getAsInt());
        }
    }
    static float value(JsonObject d,String key,int axis){return d.getAsJsonArray(key).get(axis).getAsFloat();}
    static float rad(float deg){return (float)Math.toRadians(deg);}
}
