package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritHands;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class SpiritHeldHandMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void orimod$heldOrigin(LivingEntity entity, ItemStack item, ItemDisplayContext context, boolean left,
                                 PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (SpiritHands.held(entity, item, context)) SpiritHands.beginHeld(pose, left);
    }

    @Inject(method = "renderItem", at = @At("TAIL"))
    private void orimod$paw(LivingEntity entity, ItemStack item, ItemDisplayContext context, boolean left,
                           PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (SpiritHands.held(entity, item, context)) {
            SpiritHands.heldItem(entity, item, context, pose, buffers, light);
            pose.popPose();
        }
    }
}
