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
    private static SpiritArmRig.Frame arm(boolean left,Matrix4f nativePose,Matrix4f display,String item,Vector3f local,boolean throwing) throws Exception {
        var inPalm=new Matrix4f().rotateX(rad(-70)).rotateY(rad(180)).mul(display(item,"thirdperson",left));
        var rotation=SpiritHands.palmRotations(display,inPalm);
        if(throwing)rotation=SpiritHands.spearOverhand(rotation,display);
        var posed=new Matrix4f(nativePose).translate(display.transformPosition(new Vector3f(local))).rotate(rotation);
        return SpiritArmRig.follow(left,posed.getTranslation(new Vector3f()),posed.getUnnormalizedRotation(new Quaternionf()));
    }
    private static void exitsBelow(SpiritArmRig.Frame arm) {
        float tan=.7002075f,aspect=1920f/1015;
        assertTrue(arm.shoulder().y/(-arm.shoulder().z*tan)<-1.08f,"shoulder must exit below the screen: "+arm.shoulder());
        for(int i=0;i<=40;i++) {
            var p=new Vector3f(arm.elbow()).lerp(arm.shoulder(),i/40f);
            float y=p.y/(-p.z*tan),x=p.x/(-p.z*tan*aspect);
            if(y>=-1)assertTrue(Math.abs(x)<.97f,"upper arm crossed the side edge at visible height: "+p);
        }
        assertEquals(SpiritArmRig.UPPER,arm.shoulder().distance(arm.elbow()),1e-5);
        assertEquals(SpiritArmRig.FOREARM,arm.wrist().distance(arm.elbow()),1e-5);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bowIdleUpperArmLeavesThroughBottomInsteadOfSide(boolean left) throws Exception {
        var nativePose=new Matrix4f().translation(left?-.56f:.56f,-.52f,-.72f);
        exitsBelow(arm(left,nativePose,display("spirit_bow","firstperson",left),"spirit_bow",new Vector3f(-.21875f,.21875f,0),false));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void heldSpearUsesNativeForwardAxisAwayFromCrosshairWithArmBelow(boolean left) throws Exception {
        float side=left?-1:1;
        var d=display("spirit_spear_throwing","firstperson",left);
        var override=model("spirit_spear").getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals("orimod:item/spirit_spear_throwing",override.get("model").getAsString());
        assertEquals(1,override.getAsJsonObject("predicate").get("throwing").getAsInt());
        for(int tick=0;tick<=10;tick++) {
            float pull=tick/10f;
            // ItemInHandRenderer's native SPEAR transform; production does not replace this path.
            var nativePose=new Matrix4f().translation(side*.56f,-.52f,-.72f).translate(side*-.5f,.7f,.1f)
                    .rotateX(rad(-55)).rotateY(rad(side*35.3f)).rotateZ(rad(side*-9.785f))
                    .translate(0,0,pull*.2f).scale(1,1,1+pull*.2f).rotateY(rad(-side*45));
            var item=new Matrix4f(nativePose).mul(d);
            var centre=item.transformPosition(new Vector3f());
            float screenOffset=side*centre.x/(-centre.z*.7002075f*(1920f/1015)*2);
            assertTrue(screenOffset>=.2f && screenOffset<.46f,"spear centre must be 20% of screen width off crosshair: "+screenOffset);
            var axis=item.transformDirection(new Vector3f(1,1,0)).normalize();
            var nativeAxis=new Matrix4f(nativePose).rotateY(rad(-90)).rotateZ(rad(25)).transformDirection(new Vector3f(0,1,0)).normalize();
            assertEquals(1,axis.dot(nativeAxis),1e-5,"authored diagonal aligns with native trident's forward axis");
            var arm=arm(left,nativePose,d,"spirit_spear",new Vector3f(-.12f,-.12f,0),true);
            exitsBelow(arm);
            assertTrue(arm.wrist().y<arm.palm().y,"overhand wrist must point down from the closed paw");
        }
    }
}
