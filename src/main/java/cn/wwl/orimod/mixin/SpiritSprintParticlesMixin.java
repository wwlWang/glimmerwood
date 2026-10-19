package cn.wwl.orimod.mixin;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R92: no vanilla block-chip sprint particles under a spirit; its soft light trail (SpiritFxClient) replaces them. */
@Mixin(Entity.class)
public abstract class SpiritSprintParticlesMixin {
    @Inject(method = "canSpawnSprintParticle()Z", at = @At("HEAD"), cancellable = true)
    private void orimod$noSprintChips(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && SpiritData.isSpirit(player)) cir.setReturnValue(false);
    }
}
