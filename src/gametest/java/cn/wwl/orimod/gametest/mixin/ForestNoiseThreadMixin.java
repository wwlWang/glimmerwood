package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestNoiseThreadAudit;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class ForestNoiseThreadMixin {
    @Inject(method="getBaseHeight",at=@At("HEAD"))
    private void orimod$height(CallbackInfoReturnable<Integer> result){ForestNoiseThreadAudit.record(0);cn.wwl.orimod.gametest.ForestCheapExternal.count(0);}
    @Inject(method="getBaseColumn",at=@At("HEAD"))
    private void orimod$column(CallbackInfoReturnable<?> result){ForestNoiseThreadAudit.record(1);cn.wwl.orimod.gametest.ForestCheapExternal.count(1);}
}
