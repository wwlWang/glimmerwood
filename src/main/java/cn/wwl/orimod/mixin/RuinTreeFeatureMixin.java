package cn.wwl.orimod.mixin;

import cn.wwl.orimod.world.RuinVegetationGuard;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeFeature.class)
public abstract class RuinTreeFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void orimod$keepNaturalCrownsOutsideRuins(FeaturePlaceContext<TreeConfiguration> context, CallbackInfoReturnable<Boolean> callback) {
        if (RuinVegetationGuard.blocksTree(context.level(), context.origin())) callback.setReturnValue(false);
    }
}
