package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.CreekGuardEvidence;
import cn.wwl.orimod.world.RuinVegetationGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RuinVegetationGuard.class)
abstract class CreekGuardEvidenceMixin {
    @Inject(method="blocksTree(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;I)Z",at=@At("RETURN"))
    private static void orimod$treeDecision(WorldGenLevel level,BlockPos p,int margin,CallbackInfoReturnable<Boolean> ci){
        if(CreekGuardEvidence.ENABLED)CreekGuardEvidence.decision("tree",level,p,margin,ci.getReturnValue());
    }
    @Inject(method="blocksCreekRock",at=@At("RETURN"))
    private static void orimod$rockDecision(WorldGenLevel level,BlockPos p,CallbackInfoReturnable<Boolean> ci){
        if(CreekGuardEvidence.ENABLED)CreekGuardEvidence.decision("rock",level,p,0,ci.getReturnValue());
    }
}
