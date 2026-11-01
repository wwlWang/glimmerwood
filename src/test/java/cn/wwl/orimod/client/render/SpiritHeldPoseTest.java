package cn.wwl.orimod.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for the reviewed bow shoulder and native SPEAR use silhouette. */
class SpiritHeldPoseTest {
    private static float rad(float degrees) { return (float)Math.toRadians(degrees); }
    private static JsonObject model(String name) throws Exception {
        try(var in=SpiritHeldPoseTest.class.getResourceAsStream("/assets/orimod/models/item/"+name+".json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(in))).getAsJsonObject();
        }
    }
    private static Matrix4f display(String name,String context,boolean left) throws Exception {
        var d=model(name).getAsJsonObject("display").getAsJsonObject(context+"_"+(left?"left":"right")+"hand");
        var a=d.getAsJsonArray("rotation");var t=d.getAsJsonArray("translation");var s=d.getAsJsonArray("scale");
        float sign=left?-1:1;
        return new Matrix4f().translation(sign*t.get(0).getAsFloat()/16,t.get(1).getAsFloat()/16,t.get(2).getAsFloat()/16)
                .rotateXYZ(rad(a.get(0).getAsFloat()),sign*rad(a.get(1).getAsFloat()),sign*rad(a.get(2).getAsFloat()))
                .scale(s.get(0).getAsFloat(),s.get(1).getAsFloat(),s.get(2).getAsFloat());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bowGripCoincidesWithNativeBowGrip(boolean left) throws Exception {
        var actual=display("spirit_bow","firstperson",left).transformPosition(new Vector3f(-.21875f,.21875f,0));
        var nativeDisplay=new Matrix4f().translation((left?-1:1)*1.13f/16,3.2f/16,1.13f/16)
                .rotateY(rad(-90)).rotateZ(rad(25)).scale(.68f);
        var expected=nativeDisplay.transformPosition(new Vector3f(-.21875f,.21875f,0));
        assertEquals(0,actual.distance(expected),1e-5,"JSON display keeps the native bow handle footprint");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void spearUsesNativeGripAndForwardAxisDuringEntireRaise(boolean left) throws Exception {
        float side=left?-1:1;
        var d=display("spirit_spear_throwing","firstperson",left);
        var override=model("spirit_spear").getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals("orimod:item/spirit_spear_throwing",override.get("model").getAsString());
        assertEquals(1,override.getAsJsonObject("predicate").get("throwing").getAsInt());
        var nativeDisplay=new Matrix4f().translation((left?-13:-3)/16f,17/16f,1/16f).rotateY(rad(-90)).rotateZ(rad(25));
        for(int tick=0;tick<=10;tick++) {
            float pull=tick/10f;
            var nativePose=new Matrix4f().translation(side*.56f,-.52f,-.72f).translate(side*-.5f,.7f,.1f)
                    .rotateX(rad(-55)).rotateY(rad(side*35.3f)).rotateZ(rad(side*-9.785f))
                    .translate(0,0,pull*.2f).scale(1,1,1+pull*.2f).rotateY(rad(-side*45));
            var item=new Matrix4f(nativePose).mul(d);
            var expected=new Matrix4f(nativePose).mul(nativeDisplay);
            assertEquals(0,item.transformPosition(new Vector3f(-.12f,-.12f,0))
                    .distance(expected.transformPosition(new Vector3f(-.5f,-1.25f,-.5f))),1e-5,"Native trident grip");
            var axis=item.transformDirection(new Vector3f(1,1,0)).normalize();
            var nativeAxis=expected.transformDirection(new Vector3f(0,1,0)).normalize();
            assertEquals(1,axis.dot(nativeAxis),1e-5,"authored diagonal follows native trident axis");
        }
    }
}
