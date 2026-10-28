package cn.wwl.orimod.mixin;

import cn.wwl.orimod.world.RuinVegetationGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BlockBlobFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Late adjacent-chunk forest rocks may not overwrite the creek's registered skirt. */
@Mixin(BlockBlobFeature.class)
abstract class CreekForestRockMixin {
    @WrapOperation(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean orimod$keepCreekGrade(WorldGenLevel level,BlockPos pos,BlockState state,int flags,Operation<Boolean> original){
        // Only this vanilla feature; other BlockBlobFeature instances/configured
        // features, legacy ruins, and every position outside the saved box survive.
        if((Object)this==Feature.FOREST_ROCK&&RuinVegetationGuard.blocksCreekRock(level,pos))return false;
        return original.call(level,pos,state,flags);
    }
}
