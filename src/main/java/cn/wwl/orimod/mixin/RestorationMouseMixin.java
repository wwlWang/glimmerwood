package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.RestorationClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class RestorationMouseMixin {
    @Shadow private boolean ignoreFirstMove;
    @Inject(method="onMove",at=@At("HEAD"))
    private void orimod$interrupt(long window,double x,double y,CallbackInfo ci){
        var mc=net.minecraft.client.Minecraft.getInstance();var mouse=(MouseHandler)(Object)this;
        if(window==mc.getWindow().getWindow() && mc.isWindowActive() && !ignoreFirstMove
                && (x!=mouse.xpos() || y!=mouse.ypos()))RestorationClient.mouseMoved();
    }
    /** Raw deltas before sensitivity/cinematic smoothing, followed by vanilla's normal mouse turn. */
    @Inject(method="turnPlayer",at=@At("HEAD"))
    private void orimod$guide(double movementTime,CallbackInfo ci){
        var mouse=(MouseHandler)(Object)this;
        RestorationClient.turnView(mouse.getXVelocity(),mouse.getYVelocity());
    }
}
