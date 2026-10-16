package cn.wwl.orimod.mixin;

import cn.wwl.orimod.progression.Shard;
import cn.wwl.orimod.movement.SpiritMovement;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerAirControlMixin {
    @Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
    private void orimod$airControl(CallbackInfoReturnable<Float> result) {
        var player = (Player)(Object)this;
        if (!player.onGround() && SpiritMovement.canMove(player) && Shard.AIR_CONTROL.equipped(player)) result.setReturnValue(result.getReturnValue() * 1.25f);
    }
}
