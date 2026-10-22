package cn.wwl.orimod.devtools.mixin;

import cn.wwl.orimod.client.fx.SpiritAura;
import cn.wwl.orimod.devtools.AnimationReview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpiritAura.class, remap = false)
abstract class AnimationReviewAuraMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void orimod$mute(CallbackInfo ci) { if (AnimationReview.muted()) ci.cancel(); }
}
