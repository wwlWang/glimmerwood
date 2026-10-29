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
        if(cn.wwl.orimod.gametest.ForestLavaEvidence.recording(pos)){
            long stamp=cn.wwl.orimod.gametest.ForestLavaEvidence.sectionSequence(pos);
            var before=chunk.getBlockState(pos);var result=original.call(chunk,pos,state,moving);var after=chunk.getBlockState(pos);
            cn.wwl.orimod.gametest.ForestLavaEvidence.outerWrite("WORLDGEN",pos,before,state,after,stamp);
            if(cn.wwl.orimod.gametest.ForestWaterEvidence.recording(pos))cn.wwl.orimod.gametest.ForestWaterEvidence.wrote("WORLDGEN",pos,before,state,after);
            return result;
        }
        if(cn.wwl.orimod.gametest.CreekGuardEvidence.column(pos)){
            var before=chunk.getBlockState(pos);var result=original.call(chunk,pos,state,moving);
            cn.wwl.orimod.gametest.CreekGuardEvidence.wrote(((WorldGenRegion)(Object)this).getCenter(),pos,before,state,chunk.getBlockState(pos));return result;
        }
        if(cn.wwl.orimod.gametest.ForestWaterEvidence.recording(pos)){
            var before=chunk.getBlockState(pos);var result=original.call(chunk,pos,state,moving);
            cn.wwl.orimod.gametest.ForestWaterEvidence.wrote("WORLDGEN",pos,before,state,chunk.getBlockState(pos));return result;
        }
        return (CreekWaterSourceTrace.ENABLED||CreekWaterSourceTrace.FOREIGN)?CreekWaterSourceTrace.write(chunk,pos,state,moving,original):original.call(chunk,pos,state,moving);
    }
}
