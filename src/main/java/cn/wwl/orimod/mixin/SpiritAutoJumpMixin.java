package cn.wwl.orimod.mixin;

import cn.wwl.orimod.movement.SpiritMovement;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class SpiritAutoJumpMixin {
    @Shadow private int autoJumpTime;

    // Vanilla normally applies this after MovementInputUpdateEvent, bypassing Ori's input edge.
    @Inject(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;onMovementInputUpdate(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/player/Input;)V"))
    private void orimod$collectAutoJump(CallbackInfo ci) {
        var player = (LocalPlayer)(Object)this;
        if (autoJumpTime > 0 && SpiritMovement.canMove(player)) {
            player.input.jumping = true;
            autoJumpTime = 0;
        }
    }
}
