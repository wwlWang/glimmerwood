package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestLavaEvidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;

/** Test-only aquifer source attribution; opt-in, never changes placement. */
@Mixin(ProtoChunk.class)
abstract class ForestProtoFluidEvidenceMixin {
    @WrapMethod(method="setBlockState")
    private BlockState record(BlockPos pos,BlockState state,boolean moving,Operation<BlockState> original){
        boolean whole=ForestLavaEvidence.wholeColumn(pos);
        if(!ForestLavaEvidence.target(pos)||!whole&&state.getFluidState().isEmpty())return original.call(pos,state,moving);
        var chunk=(ProtoChunk)(Object)this;var before=chunk.getBlockState(pos);long stamp=ForestLavaEvidence.sectionSequence(pos);
        var result=original.call(pos,state,moving);
        if(whole||before!=chunk.getBlockState(pos))ForestLavaEvidence.outerWrite(whole?"PROTO_COLUMN":"PROTO_FLUID",pos,before,state,chunk.getBlockState(pos),stamp);
        return result;
    }
}
