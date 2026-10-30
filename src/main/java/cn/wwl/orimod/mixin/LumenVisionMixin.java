package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.LumenSight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class LumenVisionMixin {
    /** Iris reads vanilla's scale for shader packs that compute lighting without the lightmap.
     * Keep real potion strength/duration untouched; this creates no status effect. */
    @Inject(method="getNightVisionScale",at=@At("HEAD"),cancellable=true)
    private static void orimod$lumenScale(LivingEntity entity,float partial,CallbackInfoReturnable<Float> result){
        if(entity==Minecraft.getInstance().player&&!entity.hasEffect(MobEffects.NIGHT_VISION)&&LumenSight.strength()>0)
            result.setReturnValue(LumenSight.strength());
    }
}
