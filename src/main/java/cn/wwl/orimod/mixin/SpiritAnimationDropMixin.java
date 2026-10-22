package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritAnimationEvents;
import cn.wwl.orimod.network.AnimationActionPayload.Action;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
abstract class SpiritAnimationDropMixin {
    @Inject(method = "drop", at = @At("RETURN"))
    private void orimod$drop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) SpiritAnimationEvents.local(Action.THROW, InteractionHand.MAIN_HAND);
    }
}
