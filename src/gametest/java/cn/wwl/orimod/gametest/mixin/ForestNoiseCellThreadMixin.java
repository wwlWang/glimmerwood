package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestNoiseThreadAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="cn.wwl.orimod.world.CreekSurfaceSample",remap=false)
abstract class ForestNoiseCellThreadMixin {
    @Inject(method="cell",at=@At("HEAD"))
    private static void orimod$cell(CallbackInfoReturnable<?> result){ForestNoiseThreadAudit.record(2);cn.wwl.orimod.gametest.ForestCheapExternal.count(2);}
}
