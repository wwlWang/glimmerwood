package cn.wwl.orimod.mixin;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Exact identity: modded ElytraItem subclasses retain their flight hooks. */
@Mixin(ElytraItem.class)
public abstract class KuroFeatherItemMixin {
    @Inject(method="canElytraFly",at=@At("HEAD"),cancellable=true)
    private void feather(ItemStack s,LivingEntity e,CallbackInfoReturnable<Boolean> c){if(s.is(Items.ELYTRA) && e instanceof net.minecraft.world.entity.player.Player p && cn.wwl.orimod.spirit.SpiritData.isSpirit(p))c.setReturnValue(false);}
    @Inject(method="elytraFlightTick",at=@At("HEAD"),cancellable=true)
    private void noWear(ItemStack s,LivingEntity e,int ticks,CallbackInfoReturnable<Boolean> c){if(s.is(Items.ELYTRA) && e instanceof net.minecraft.world.entity.player.Player p && cn.wwl.orimod.spirit.SpiritData.isSpirit(p))c.setReturnValue(false);}
}
