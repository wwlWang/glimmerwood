package cn.wwl.orimod.mixin;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ElytraLayer.class)
public abstract class FeatherElytraLayerMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void hide(ItemStack stack,LivingEntity e,CallbackInfoReturnable<Boolean> c){if(stack.is(Items.ELYTRA) && e instanceof net.minecraft.world.entity.player.Player p && cn.wwl.orimod.spirit.SpiritData.isSpirit(p))c.setReturnValue(false);}
}
