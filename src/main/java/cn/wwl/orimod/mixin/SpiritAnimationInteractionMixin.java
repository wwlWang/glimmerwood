package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritAnimationEvents;
import cn.wwl.orimod.network.AnimationActionPayload.Action;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class SpiritAnimationInteractionMixin {
    @Unique private ItemStack orimod$used = ItemStack.EMPTY;
    @Inject(method = "attack", at = @At("HEAD"))
    private void orimod$attack(Player player, Entity target, CallbackInfo ci) {
        SpiritAnimationEvents.of(player).action = null;
    }
    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("RETURN"))
    private void orimod$mine(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) SpiritAnimationEvents.local(Action.MINE, InteractionHand.MAIN_HAND);
    }
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void orimod$place(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().shouldSwing()) SpiritAnimationEvents.local(Action.PLACE, hand);
    }
    @Inject(method = "useItem", at = @At("HEAD"))
    private void orimod$remember(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        orimod$used = player.getItemInHand(hand).copy(); // The last snowball may be consumed by the successful call.
    }
    @Inject(method = "useItem", at = @At("RETURN"))
    private void orimod$use(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().shouldSwing()) return;
        var item = orimod$used.getItem();
        if (orimod$used.is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)) SpiritAnimationEvents.local(Action.SPEAR_THROW, hand);
        else if (item instanceof SnowballItem || item instanceof EnderpearlItem || item instanceof EggItem
                || item instanceof ThrowablePotionItem || item instanceof ExperienceBottleItem)
            SpiritAnimationEvents.local(Action.THROW, hand);
        else if (!player.isUsingItem()) SpiritAnimationEvents.local(Action.PLACE, hand);
    }
    @Inject(method = "interact", at = @At("RETURN"))
    private void orimod$interact(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().shouldSwing()) SpiritAnimationEvents.local(Action.PLACE, hand);
    }
    @Inject(method = "interactAt", at = @At("RETURN"))
    private void orimod$interactAt(Player player, Entity target, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().shouldSwing()) SpiritAnimationEvents.local(Action.PLACE, hand);
    }
}
