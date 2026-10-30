package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritAnimationEvents;
import cn.wwl.orimod.combat.SpearCharge;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.network.AnimationActionPayload.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class SpiritSpearReleaseMixin {
    @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
    private void orimod$minimumSpearCharge(Player player, CallbackInfo ci) {
        if (!player.isUsingItem() || !player.getUseItem().is(OriItems.SPIRIT_SPEAR)) return;
        var mc = Minecraft.getInstance();
        // Vanilla retries release on every tick with the use key up. Keep its real using-item
        // state for four ticks so a quick tap also gets the native SPEAR animation.
        if (mc.screen == null && mc.isWindowActive() && SpearCharge.deferRelease(player.getTicksUsingItem())) ci.cancel();
        else SpiritAnimationEvents.local(Action.SPEAR_THROW, player.getUsedItemHand());
    }
}
