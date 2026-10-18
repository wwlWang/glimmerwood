package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class SpiritInventoryMixin {
    @Inject(method = "renderEntityInInventory", at = @At("HEAD"), cancellable = true)
    private static void orimod$inventory(GuiGraphics graphics, float x, float y, float scale, Vector3f translate,
                                         Quaternionf pose, Quaternionf cameraOrientation, LivingEntity entity, CallbackInfo ci) {
        if (SpiritInventory.render(graphics, x, y, scale, translate, pose, entity)) ci.cancel();
    }
}
