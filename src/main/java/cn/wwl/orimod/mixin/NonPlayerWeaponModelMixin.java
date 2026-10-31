package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.NonPlayerWeaponModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public abstract class NonPlayerWeaponModelMixin {
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void orimod$displayWeapon(ItemStack stack, Level level, LivingEntity holder, int seed, CallbackInfoReturnable<BakedModel> cir) {
        if (holder == null || holder instanceof Player) return;
        var model = NonPlayerWeaponModels.model(stack);
        if (model != null) cir.setReturnValue(Minecraft.getInstance().getModelManager().getModel(model));
    }
}
