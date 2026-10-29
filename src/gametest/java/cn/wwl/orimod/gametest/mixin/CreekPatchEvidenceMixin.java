package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.CreekGuardEvidence;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.RandomPatchFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RandomPatchFeature.class)
abstract class CreekPatchEvidenceMixin {
    @WrapOperation(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean orimod$attempt(PlacedFeature feature,WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos,Operation<Boolean> original){
        boolean observe=CreekGuardEvidence.mushroomPatch(pos);
        if(observe)CreekGuardEvidence.mushroom("patch_attempt_begin",level,pos,false);
        boolean result=original.call(feature,level,generator,random,pos);
        if(observe)CreekGuardEvidence.mushroom("patch_attempt_end",level,pos,result);
        return result;
    }
}
