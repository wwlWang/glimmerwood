package cn.wwl.orimod.client.render;

import cn.wwl.orimod.client.fx.BladeSlash;
import cn.wwl.orimod.combat.SpiritCombat;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native arm space replaces the old camera-framing limits. Existing slash-envelope contracts remain. */
class SpiritBladeFramingTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void emptyHandPreservesTheWholeVanillaAnimationMatrix(boolean left) {
        var calibration=new PoseStack();SpiritHands.emptyPose(calibration,left);
        for(float t=0;t<=1;t+=.05f) {
            var input=new Matrix4f().translation(left?-.64f:.64f,-.6f-.6f*t,-.72f)
                    .rotateXYZ(t*3,t*2,t).scale(1,1,1);
            var animated=new PoseStack();animated.mulPose(input);SpiritHands.emptyPose(animated,left);
            assertTrue(new Matrix4f(input).mul(calibration.last().pose()).equals(animated.last().pose(),1e-6f));
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualEmptyPawCannotReachPastTheNativeArmEndpoint(boolean left) throws Exception {
        byte[] bytes;
        try(var in=getClass().getResourceAsStream("/assets/orimod/models/entity/paw_"+(left?"left":"right")+"_open.glb")) {
            bytes=java.util.Objects.requireNonNull(in).readAllBytes();
        }
        int length=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(12);
        var gltf=com.google.gson.JsonParser.parseString(new String(bytes,20,length,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        int position=gltf.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject().getAsJsonObject("attributes").get("POSITION").getAsInt();
        var accessor=gltf.getAsJsonArray("accessors").get(position).getAsJsonObject();
        var pose=new PoseStack();SpiritHands.emptyPose(pose,left);
        for(String key:new String[]{"min","max"}) {
            var b=accessor.getAsJsonArray(key);var point=new org.joml.Vector3f(b.get(0).getAsFloat(),b.get(1).getAsFloat(),b.get(2).getAsFloat());
            pose.last().pose().transformPosition(point);
            assertTrue(point.y>=-.001f && point.y<=.75f,"Actual arm exceeds native 12-pixel length: "+point);
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void remoteArcKeepsItsTail(int stage) {
        assertEquals(6f/7,BladeSlash.opacity(stage,SpiritCombat.recoveryStart(stage)+1,false),1e-6);
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void firstPersonArcHasNoRecoveryPixels(int stage) {
        for(float t=SpiritCombat.recoveryStart(stage);t<SpiritCombat.duration(stage);t+=.1f)
            assertEquals(0,BladeSlash.opacity(stage,t,true),1e-6);
    }
}
