package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestLavaEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FlowingFluid.class)
abstract class ForestLavaTickMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void before(Level level,BlockPos pos,FluidState state,CallbackInfo ci){ForestLavaEvidence.fluidTick(level,pos,state,true);}
    @Inject(method="tick",at=@At("RETURN"))
    private void after(Level level,BlockPos pos,FluidState state,CallbackInfo ci){ForestLavaEvidence.fluidTick(level,pos,state,false);}
}
