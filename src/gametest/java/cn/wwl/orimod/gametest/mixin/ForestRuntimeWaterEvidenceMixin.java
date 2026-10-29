package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestWaterEvidence;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Runtime fluid-tick attribution, only in diagnostic test jar and explicitly opted in. */
@Mixin(Level.class)
abstract class ForestRuntimeWaterEvidenceMixin {
    @WrapOperation(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState orimod$runtimeEvidence(LevelChunk chunk,BlockPos pos,BlockState state,boolean moving,Operation<BlockState> original){
        if(!ForestWaterEvidence.target(pos)&&!cn.wwl.orimod.gametest.ForestLavaEvidence.recording(pos))return original.call(chunk,pos,state,moving);
        long stamp=cn.wwl.orimod.gametest.ForestLavaEvidence.sectionSequence(pos);
        var before=chunk.getBlockState(pos);var result=original.call(chunk,pos,state,moving);
        ForestWaterEvidence.wrote("RUNTIME",pos,before,state,chunk.getBlockState(pos));
        cn.wwl.orimod.gametest.ForestLavaEvidence.outerWrite("RUNTIME",pos,before,state,chunk.getBlockState(pos),stamp);return result;
    }
}
