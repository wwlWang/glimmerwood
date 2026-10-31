package cn.wwl.orimod.mixin;

import cn.wwl.orimod.movement.VanillaMotion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class SpiritRiptideMixin {
    @Inject(method = "startAutoSpinAttack", at = @At("TAIL"))
    private void orimod$vanillaFlight(int ticks, float damage, ItemStack stack, CallbackInfo ci) {
        VanillaMotion.startRiptide((Player)(Object)this);
    }
}
