package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.RestorationClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class RestorationKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void orimod$skip(long window,int key,int scanCode,int action,int modifiers,CallbackInfo ci){
        if(window==net.minecraft.client.Minecraft.getInstance().getWindow().getWindow() && RestorationClient.escape(key,action))ci.cancel();
    }
}
