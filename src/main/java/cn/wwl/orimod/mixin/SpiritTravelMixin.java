package cn.wwl.orimod.mixin;

import cn.wwl.orimod.movement.SpiritPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LivingEntity.class)
abstract class SpiritTravelMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void orimod$newJump(CallbackInfo ci) {
        if ((Object)this instanceof Player player) cn.wwl.orimod.movement.SlimeBounce.clear(player);
    }

    @Inject(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void orimod$effectiveGravity(Vec3 input, CallbackInfo ci, double gravity, boolean falling) {
        if ((Object)this instanceof Player player) SpiritPhysics.beforeTravel(player, gravity);
    }
}
