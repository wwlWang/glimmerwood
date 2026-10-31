package cn.wwl.orimod.mixin;

import cn.wwl.orimod.world.RuinVegetationGuard;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancel only the intersecting lake; outside lakes keep the vanilla RNG/path. */
@Mixin(LakeFeature.class)
abstract class ForestLakeMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void orimod$preserveForest(FeaturePlaceContext<LakeFeature.Configuration> context,CallbackInfoReturnable<Boolean> result){
        if((RuinVegetationGuard.blocksForestLake(context.level(),context.origin()) || RuinVegetationGuard.blocksFeatureLake(context.level(),context.origin())))result.setReturnValue(false);
    }
}
