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
    void old64SizeAndVisibleGripAreAuthoredInTheItemDisplay(Weapon weapon) throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/orimod/models/item/spirit_"+weapon.name().toLowerCase(java.util.Locale.ROOT)+".json")) {
            var displays=JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(in))).getAsJsonObject().getAsJsonObject("display");
            for(boolean left:new boolean[]{false,true}) {
                var d=displays.getAsJsonObject("firstperson_"+(left?"left":"right")+"hand");
                assertEquals(JsonParser.parseString(left?"[0,90,-25]":"[0,-90,25]"),d.get("rotation"));
                // old64's (0.5,2,1)/16 display plus its (-.10,.22,-.20) rest offset,
                // now entirely in item space so native swing/equip/use transforms still act on it.
                String translation="[-1.1,5.52,-2.2]";
                String scale=switch(weapon) {
                    case BLADE -> "[0.48,0.48,0.48]";
                    case BOW -> "[0.58,0.58,0.58]";
                    case HAMMER -> "[0.52,0.52,0.52]";
                    case SPEAR -> "[0.56,0.56,0.56]";
                };
                assertEquals(JsonParser.parseString(translation),d.get("translation"));
                assertEquals(JsonParser.parseString(scale),d.get("scale"));
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
