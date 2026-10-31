package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.SpiritCombatClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resource/held-key refusal applies to the weapon, never to the earlier vanilla interaction. */
@Mixin(MultiPlayerGameMode.class)
public abstract class SpiritWeaponInteractionMixin {
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void orimod$weaponUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SpiritCombatClient.refuseItemUse(hand)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
