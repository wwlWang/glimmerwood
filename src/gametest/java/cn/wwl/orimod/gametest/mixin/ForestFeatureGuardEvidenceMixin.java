package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestFeatureSiteEvidence;
import cn.wwl.orimod.world.RuinVegetationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RuinVegetationGuard.class)
abstract class ForestFeatureGuardEvidenceMixin {
    @Inject(method="protects(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/core/BlockPos;I)Z",at=@At("RETURN"))
    private static void guard(BoundingBox box,BlockPos pos,int margin,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValueZ())ForestFeatureSiteEvidence.guard(box);
    }
}
