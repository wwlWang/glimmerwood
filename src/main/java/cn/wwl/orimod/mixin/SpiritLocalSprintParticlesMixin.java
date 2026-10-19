package cn.wwl.orimod.mixin;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R92: LocalPlayer overrides the sprint-particle check; same rule as {@link SpiritSprintParticlesMixin}. */
@Mixin(LocalPlayer.class)
public abstract class SpiritLocalSprintParticlesMixin {
    @Inject(method = "canSpawnSprintParticle()Z", at = @At("HEAD"), cancellable = true)
    private void orimod$noSprintChips(CallbackInfoReturnable<Boolean> cir) {
        if (SpiritData.isSpirit((LocalPlayer) (Object) this)) cir.setReturnValue(false);
    }
}
