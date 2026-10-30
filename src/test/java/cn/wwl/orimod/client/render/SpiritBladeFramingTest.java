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
