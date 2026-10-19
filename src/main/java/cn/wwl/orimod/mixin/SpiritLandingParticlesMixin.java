package cn.wwl.orimod.mixin;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R92: a spirit lands soft and takes no fall damage (R84); clear the fall distance before vanilla's landing check so it
 * also skips the burst of opaque block-chip particles, which read as hard black squares around Ori at night.
 */
@Mixin(LivingEntity.class)
public abstract class SpiritLandingParticlesMixin {
    @Inject(method = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private void orimod$softLanding(double y, boolean onGround, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (onGround && (Object) this instanceof Player player && SpiritData.isSpirit(player)) player.fallDistance = 0;
    }
}
