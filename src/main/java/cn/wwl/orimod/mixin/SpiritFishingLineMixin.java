package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritHeldItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHookRenderer.class)
abstract class SpiritFishingLineMixin {
    @Inject(method = "getPlayerHandPos", at = @At("HEAD"), cancellable = true)
    private void orimod$rodTip(Player player, float swing, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        var mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        var tip = SpiritHeldItems.fishingLineOrigin(player, partialTick);
        if (tip != null) cir.setReturnValue(tip);
    }
}
