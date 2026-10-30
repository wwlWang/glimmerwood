package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SpiritFirstPersonMotionTest {
    @Test void airSlamStartsAndReturnsToTheUnmodifiedVanillaPose() {
        assertTrue(new Matrix4f().equals(SpiritFirstPersonMotion.plunge(0),1e-6f));
        assertEquals(0,SpiritFirstPersonMotion.plungeWeight(true,0,0));
        assertEquals(1,SpiritFirstPersonMotion.plungeWeight(true,4,0));
        assertEquals(1,SpiritFirstPersonMotion.plungeWeight(false,0,0));
        assertEquals(0,SpiritFirstPersonMotion.plungeWeight(false,0,8));
        assertEquals(0,SpiritFirstPersonMotion.plungeWeight(false,0,100));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void airSlamDoesNotMoveTheHandAcrossTheScreen(boolean left) {
        float side=left?-1:1;
        for(float t=0;t<=12;t+=.05f) {
            float weight=SpiritFirstPersonMotion.plungeWeight(t<4,t,t-4);
            var pose=new Matrix4f().translation(side*.56f,-.52f,-.72f).mul(SpiritFirstPersonMotion.plunge(weight));
            assertEquals(side*.56f,pose.getTranslation(new Vector3f()).x,1e-6);
            assertTrue(pose.isFinite());
        }
    }
    @Test void palmKeepsTheThirdPersonGripInAnyView() {
        var itemInPalm=new Quaternionf().rotationXYZ(.4f,-1.2f,.9f);
        var itemInView=new Quaternionf().rotationXYZ(-2.9f,.3f,2.2f);
        var palm=SpiritItemGrip.palmFromItem(itemInView,itemInPalm);
        assertEquals(1,Math.abs(new Quaternionf(palm).mul(itemInPalm).dot(itemInView)),1e-6);
        var handle=new Vector3f(1,1,0).normalize();
        assertEquals(0,palm.transform(itemInPalm.transform(new Vector3f(handle))).distance(itemInView.transform(new Vector3f(handle))),1e-6);
    }

    @ParameterizedTest @ValueSource(floats={.48f,.68f,.9f,1f,1.25f})
    void displayScaleCannotChangePalmOrThumbOrientation(float scale) {
        var inView=new Quaternionf().rotationXYZ(.3f,-1.57f,.43f);
        var inPalm=new Quaternionf().rotationXYZ(-1.2f,1.57f,.96f);
        var actual=SpiritHands.palmRotations(new Matrix4f().rotate(inView).scale(scale),
                new Matrix4f().rotate(inPalm).scale(.85f));
        assertEquals(1,Math.abs(new Quaternionf(actual).mul(inPalm).dot(inView)),1e-6);
    }
}
