package cn.wwl.orimod.mixin;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.world.GreatTreeSites;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the old post-prepareLevels ordering, but finish before DedicatedServer starts its watchdog. */
@Mixin(MinecraftServer.class)
public abstract class GreatTreePreparationMixin {
    @Unique private long orimod$loadStarted;
    @Inject(method="loadLevel",at=@At("HEAD"))
    private void orimod$startLoad(CallbackInfo ci){orimod$loadStarted=System.nanoTime();}
    @Inject(method="loadLevel",at=@At("TAIL"))
    private void orimod$prepareSite(CallbackInfo ci){
        GreatTreeSites.prepare((MinecraftServer)(Object)this);
        OriMod.LOGGER.info("World loadLevel including Great Spirit Tree preparation: {} ms",(System.nanoTime()-orimod$loadStarted)/1e6);
    }
}
