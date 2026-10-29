package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestCheapExternal;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Structure.GenerationStub.class)
abstract class ForestCheapBuilderBoundaryMixin {
    @Inject(method="getPiecesBuilder",at=@At("HEAD"))
    private void orimod$builder(CallbackInfoReturnable<?> cir){ForestCheapExternal.count(4);}
}
