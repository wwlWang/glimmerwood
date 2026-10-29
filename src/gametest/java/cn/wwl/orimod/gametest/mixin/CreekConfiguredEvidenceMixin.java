package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.CreekGuardEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfiguredFeature.class)
abstract class CreekConfiguredEvidenceMixin {
    @Inject(method="place",at=@At("HEAD"))
    private void orimod$featureBegin(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos,CallbackInfoReturnable<Boolean> ci){
        cn.wwl.orimod.gametest.ForestLavaEvidence.feature(level,(ConfiguredFeature<?,?>)(Object)this,pos,true,false);
        if(CreekGuardEvidence.ENABLED)CreekGuardEvidence.feature(level,(ConfiguredFeature<?,?>)(Object)this,pos,true,false);
    }
    @Inject(method="place",at=@At("RETURN"))
    private void orimod$featureEnd(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos,CallbackInfoReturnable<Boolean> ci){
        cn.wwl.orimod.gametest.ForestLavaEvidence.feature(level,(ConfiguredFeature<?,?>)(Object)this,pos,false,ci.getReturnValue());
        if(CreekGuardEvidence.ENABLED)CreekGuardEvidence.feature(level,(ConfiguredFeature<?,?>)(Object)this,pos,false,ci.getReturnValue());
    }
}
