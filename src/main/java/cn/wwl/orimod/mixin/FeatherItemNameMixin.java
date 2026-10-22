package cn.wwl.orimod.mixin;
import cn.wwl.orimod.client.FeatherItemAppearance;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Client view only; item data and server-side names are untouched, including custom anvil names. */
@Mixin(ItemStack.class)
public abstract class FeatherItemNameMixin {
    @Inject(method="getHoverName",at=@At("RETURN"),cancellable=true)
    private void featherName(CallbackInfoReturnable<Component> c){
        var stack=(ItemStack)(Object)this;
        if(com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() && stack.is(Items.ELYTRA) && !stack.has(DataComponents.CUSTOM_NAME) && !stack.has(DataComponents.ITEM_NAME)
                && FeatherItemAppearance.spirit())c.setReturnValue(Component.translatable("item.orimod.kuro_feather"));
    }
}
