package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.CreekWaterWrites;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets="cn.wwl.orimod.world.RuinTerrainKernel")
abstract class CreekWaterWritesMixin {
    @Shadow @Final private BlockPos origin;
    @WrapOperation(method={"place","writeGuard","supportFoundations"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean orimod$observeWater(WorldGenLevel level,BlockPos pos,BlockState state,int flags,Operation<Boolean> original){return CreekWaterWrites.write(level,origin,pos,state,flags,original);}
}
