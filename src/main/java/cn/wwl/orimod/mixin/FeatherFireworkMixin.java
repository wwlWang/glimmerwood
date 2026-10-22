package cn.wwl.orimod.mixin;
import cn.wwl.orimod.movement.FeatherGlide;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FireworkRocketItem.class)
public abstract class FeatherFireworkMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void glide(Level l,Player p,InteractionHand h,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> c){
        if(FeatherGlide.refuseFirework(p))c.setReturnValue(InteractionResultHolder.fail(p.getItemInHand(h)));
    }
    @Inject(method="useOn",at=@At("HEAD"),cancellable=true)
    private void glideBlock(UseOnContext ctx,CallbackInfoReturnable<InteractionResult> c){
        if(ctx.getPlayer()!=null && FeatherGlide.refuseFirework(ctx.getPlayer()))c.setReturnValue(InteractionResult.FAIL);
    }
}
