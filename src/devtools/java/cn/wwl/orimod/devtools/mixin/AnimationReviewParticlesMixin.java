package cn.wwl.orimod.devtools.mixin;

import cn.wwl.orimod.devtools.AnimationReview;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
abstract class AnimationReviewParticlesMixin {
    // Shader/particle integrations may render from their own pass; suppress admission as well.
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void orimod$admission(CallbackInfo ci) {
        if (AnimationReview.muted()) { AnimationReview.particleSuppressed(); ci.cancel(); }
    }
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void orimod$mute(CallbackInfo ci) { if (AnimationReview.muted()) ci.cancel(); }
}
