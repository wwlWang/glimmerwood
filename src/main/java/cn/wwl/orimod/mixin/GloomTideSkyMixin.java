package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.fx.GloomTideClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla sky/star return values only; no renderer internals or shader-specific assumptions. */
@Mixin(ClientLevel.class)
public abstract class GloomTideSkyMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void orimod$tideSky(Vec3 pos, float partial, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(GloomTideClient.sky(cir.getReturnValue(), partial));
    }
    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void orimod$tideStars(float partial, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GloomTideClient.stars(cir.getReturnValue(), partial));
    }
}
