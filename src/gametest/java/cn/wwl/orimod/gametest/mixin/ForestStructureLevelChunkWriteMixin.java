package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestStructureEvidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(LevelChunk.class)
abstract class ForestStructureLevelChunkWriteMixin {
    @WrapMethod(method="setBlockState")
    private BlockState write(BlockPos pos,BlockState state,boolean moving,Operation<BlockState> original){
        if(!ForestStructureEvidence.recording()||ForestStructureEvidence.nestedWrite(pos))return original.call(pos,state,moving);
        var chunk=(LevelChunk)(Object)this;var before=chunk.getBlockState(pos);ForestStructureEvidence.enterWrite(pos);
        try{var result=original.call(pos,state,moving);ForestStructureEvidence.write(pos,before,state,chunk.getBlockState(pos));return result;}
        finally{ForestStructureEvidence.exitWrite();}
    }
}
