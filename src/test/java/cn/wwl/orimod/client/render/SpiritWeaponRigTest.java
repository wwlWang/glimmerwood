package cn.wwl.orimod.client.render;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Replaces camera framing with authored item displays and unmodified vanilla animation matrices. */
class SpiritWeaponRigTest {
    enum Weapon { BLADE, BOW, HAMMER, SPEAR }
    @ParameterizedTest @EnumSource(Weapon.class)
    void authoredDisplaysKeepNativeAxesAndReadableWeaponFootprints(Weapon weapon) throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/orimod/models/item/spirit_"+weapon.name().toLowerCase(java.util.Locale.ROOT)+".json")) {
            var displays=JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(in))).getAsJsonObject().getAsJsonObject("display");
            for(boolean left:new boolean[]{false,true}) {
                var d=displays.getAsJsonObject("firstperson_"+(left?"left":"right")+"hand");
                var t=d.getAsJsonArray("translation");var r=d.getAsJsonArray("rotation");var scale=d.getAsJsonArray("scale");float sign=left?-1:1;
                var actual=new Matrix4f().translation(sign*t.get(0).getAsFloat()/16,t.get(1).getAsFloat()/16,t.get(2).getAsFloat()/16)
                        .rotateXYZ((float)Math.toRadians(r.get(0).getAsFloat()),sign*(float)Math.toRadians(r.get(1).getAsFloat()),sign*(float)Math.toRadians(r.get(2).getAsFloat()))
                        .scale(scale.get(0).getAsFloat(),scale.get(1).getAsFloat(),scale.get(2).getAsFloat());
                assertEquals(scale.get(0),scale.get(1),"Uniform authored size");assertEquals(scale.get(0),scale.get(2));
                float size=scale.get(0).getAsFloat();
                assertTrue(size >= switch(weapon){case BLADE->.68f;case HAMMER->.7f;case BOW->.6f;case SPEAR->1.1f;});
                assertTrue(size <= switch(weapon){case BLADE->.8f;case HAMMER->1f;case BOW->.75f;case SPEAR->1.5f;});
                var grip=switch(weapon){case BLADE->new org.joml.Vector3f(-.125f,-.125f,0);case HAMMER->new org.joml.Vector3f(-.2f,-.2f,0);case BOW->new org.joml.Vector3f(-.21875f,.21875f,0);case SPEAR->new org.joml.Vector3f(-.12f,-.12f,0);};
                var nativeDisplay=weapon==Weapon.SPEAR?new Matrix4f().translation((left?-13:-3)/16f,17/16f,1/16f)
                        :new Matrix4f().translation(sign*1.13f/16,3.2f/16,1.13f/16);
                nativeDisplay.rotateY(-(float)Math.PI/2).rotateZ((float)Math.toRadians(25)).scale(weapon==Weapon.SPEAR?1:.68f);
                var nativeGrip=weapon==Weapon.SPEAR?new org.joml.Vector3f(-.5f,-1.25f,-.5f):weapon==Weapon.BOW
                        ?new org.joml.Vector3f(-.21875f,.21875f,0):new org.joml.Vector3f(-.125f,-.125f,0);
                if (weapon != Weapon.HAMMER) {
                    assertEquals(0,new Matrix4f(actual).transformPosition(grip).distance(nativeDisplay.transformPosition(nativeGrip)),1e-5,"Native handle footprint");
                } else {
                    // A broad hammer head needs clearance that a sword's narrow tip does not.
                    // Its display, not a camera-space clamp, keeps the entire dense T head in frame.
                    var projected = new Matrix4f().perspective((float)Math.toRadians(70),1280f/720,.05f,10)
                            .translate(sign*.56f,-.52f,-.72f).mul(actual);
                    var anchor = projected.transformProject(new org.joml.Vector3f(-.2f,-.2f,0));
                    assertTrue(sign*anchor.x>.7f && anchor.y<-.75f,
                            "Hammer grip must meet the lower hand-side edge, not float mid-screen: "+anchor);
                    var headCenter = projected.transformProject(new org.joml.Vector3f(.24f,.24f,0));
                    assertTrue(sign*headCenter.x>.4f && headCenter.y>0,
                            "Hammer head must remain above its anchored grip on the hand side: "+headCenter);
                    for(float end:new float[]{0,1})for(float edge:new float[]{-.13f,.13f}) {
                        float offset=edge/(float)Math.sqrt(2);
                        var vertex=projected.transformProject(new org.joml.Vector3f(.02f+.44f*end+offset,.46f-.44f*end+offset,0));
                        assertTrue(Math.abs(vertex.x)<.98f && Math.abs(vertex.y)<.98f,"Hammer head clipped at idle: "+vertex);
                    }
                }
                var axis=actual.transformDirection(new org.joml.Vector3f(1,1,0)).normalize();
                var nativeAxis=nativeDisplay.transformDirection(weapon==Weapon.SPEAR?new org.joml.Vector3f(0,1,0):new org.joml.Vector3f(1,1,0)).normalize();
                if (weapon != Weapon.HAMMER) assertEquals(1,axis.dot(nativeAxis),1e-5,"Native item inclination");
                else assertTrue(axis.dot(nativeAxis)>.85f,"Broad hammer remains near the native handheld inclination");
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void itemHookPreservesAllIncomingSwingEquipBobAndUseMatrices(boolean left) {
        for(float progress:new float[]{0,.1f,.25f,.5f,.75f,1}) {
            var pose=new PoseStack();
            pose.translate(left?-.56f:.56f,-.52f-.6f*progress,-.72f);
            pose.mulPose(new Matrix4f().rotateXYZ(progress*1.5f,progress*.7f,-progress*.4f));
            pose.scale(1,1,1+progress*.2f); // Native bow/trident use stretch belongs to the item.
            var original=new Matrix4f(pose.last().pose());
            SpiritHands.beginHeld(null,null,null,pose,left);
            assertEquals(original,pose.last().pose(),"No repositioning, clamps, fixed camera assembly or equip suppression");
            pose.popPose();assertEquals(original,pose.last().pose());
        }
    }
    @ParameterizedTest @EnumSource(net.minecraft.world.entity.HumanoidArm.class)
    void sameWeaponInOffHandDoesNotBorrowMainHandAction(net.minecraft.world.entity.HumanoidArm arm) {
        for(var context:net.minecraft.world.item.ItemDisplayContext.values()) {
            boolean main=context==(arm==net.minecraft.world.entity.HumanoidArm.LEFT
                    ?net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    :net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            assertEquals(main,SpiritFirstPersonGrip.mainHandView(context,arm));
        }
    }
}
