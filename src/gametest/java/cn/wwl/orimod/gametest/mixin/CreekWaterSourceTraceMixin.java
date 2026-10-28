package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.CreekWaterSourceTrace;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldGenRegion.class)
abstract class CreekWaterSourceTraceMixin {
    @WrapOperation(method="setBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState orimod$attributeWater(ChunkAccess chunk,BlockPos pos,BlockState state,boolean moving,Operation<BlockState> original){
        cn.wwl.orimod.gametest.CreekForeignTerrainTrace.wrote(pos);
        return (CreekWaterSourceTrace.ENABLED||CreekWaterSourceTrace.FOREIGN)?CreekWaterSourceTrace.write(chunk,pos,state,moving,original):original.call(chunk,pos,state,moving);
    }
}
