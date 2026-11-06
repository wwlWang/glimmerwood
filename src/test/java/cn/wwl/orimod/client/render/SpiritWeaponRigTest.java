package cn.wwl.orimod.client.render;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
            Vector3f rightMid = null, rightTipDir = null;
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
                var grip=switch(weapon){case BLADE->new Vector3f(-.125f,-.125f,0);case HAMMER->new Vector3f(-.2f,-.2f,0);case BOW->new Vector3f(-.21875f,.21875f,0);case SPEAR->new Vector3f(-.12f,-.12f,0);};
                var projected = new Matrix4f().perspective((float)Math.toRadians(70),1280f/720,.05f,10)
                        .translate(sign*.56f,-.52f,-.72f).mul(actual);
                if (weapon == Weapon.HAMMER) {
                    var anchor = projected.transformProject(new Vector3f(-.2f,-.2f,0));
                    assertTrue(sign*anchor.x>.7f && anchor.y<-.75f,
                            "Hammer grip must meet the lower hand-side edge, not float mid-screen: "+anchor);
                    var headCenter = projected.transformProject(new Vector3f(.24f,.24f,0));
                    assertTrue(sign*headCenter.x>.4f && headCenter.y>0,
                            "Hammer head must remain above its anchored grip on the hand side: "+headCenter);
                    for(float end:new float[]{0,1})for(float edge:new float[]{-.13f,.13f}) {
                        float offset=edge/(float)Math.sqrt(2);
                        var vertex=projected.transformProject(new Vector3f(.02f+.44f*end+offset,.46f-.44f*end+offset,0));
                        assertTrue(Math.abs(vertex.x)<.98f && Math.abs(vertex.y)<.98f,"Hammer head clipped at idle: "+vertex);
                    }
                } else if (weapon == Weapon.SPEAR) {
                    var nativeDisplay=new Matrix4f().translation((left?-13:-3)/16f,17/16f,1/16f);
                    nativeDisplay.rotateY(-(float)Math.PI/2).rotateZ((float)Math.toRadians(25)).scale(1);
                    assertEquals(0,new Matrix4f(actual).transformPosition(grip).distance(nativeDisplay.transformPosition(new Vector3f(-.5f,-1.25f,-.5f))),1e-5,"Native handle footprint");
                    var axis=actual.transformDirection(new Vector3f(1,1,0)).normalize();
                    var nativeAxis=nativeDisplay.transformDirection(new Vector3f(0,1,0)).normalize();
                    assertEquals(1,axis.dot(nativeAxis),1e-5,"Native item inclination");
                } else {
                    // Blade / bow: raised above the hotbar so the mid-blade / limbs stay readable in FP.
                    // Exact diamond-sword Y was abandoned after playtest showed a white edge strip.
                    var mid = projected.transformProject(weapon == Weapon.BOW
                            ? new Vector3f(-.05f, .35f, 0) : new Vector3f(.08f, .08f, 0));
                    var handle = projected.transformProject(new Vector3f(grip));
                    assertTrue(sign*handle.x > .35f, "Handle stays on its hand side: "+handle);
                    assertTrue(handle.y < -.20f, "Handle stays in the lower half: "+handle);
                    assertTrue(Math.abs(mid.x) < .98f && Math.abs(mid.y) < .95f, "Mid weapon clipped: "+mid);
                    if (weapon == Weapon.BLADE) assertTrue(mid.y > handle.y, "Blade rises above the grip");
                    var tipDir = actual.transformDirection(new Vector3f(1,1,0)).normalize();
                    assertTrue(tipDir.y > .25f, "Weapon aims upward in hand space: "+tipDir);
                    if (!left) { rightMid = new Vector3f(mid); rightTipDir = new Vector3f(tipDir); }
                    else {
                        assertEquals(rightMid.x, -mid.x, .05f, "Left mid mirrors right in X");
                        assertEquals(rightMid.y, mid.y, .05f, "Left mid matches right height");
                        assertEquals(rightTipDir.y, tipDir.y, 1e-4f, "Left/right share tip elevation");
                        assertEquals(rightTipDir.z, tipDir.z, 1e-4f, "Left/right share tip depth");
                    }
                }
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
