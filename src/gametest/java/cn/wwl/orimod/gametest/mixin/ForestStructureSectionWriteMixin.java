package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestStructureEvidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(LevelChunkSection.class)
abstract class ForestStructureSectionWriteMixin {
    @WrapMethod(method="setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;")
    private BlockState write(int x,int y,int z,BlockState state,boolean lock,Operation<BlockState> original){
        if(!ForestStructureEvidence.recording())return original.call(x,y,z,state,lock);
        var section=(LevelChunkSection)(Object)this;var pos=ForestStructureEvidence.sectionPosition(section,x,y,z);if(pos==null||ForestStructureEvidence.nestedWrite(pos))return original.call(x,y,z,state,lock);
        var before=section.getBlockState(x,y,z);
        var result=original.call(x,y,z,state,lock);ForestStructureEvidence.write(pos,before,state,section.getBlockState(x,y,z));return result;
    }
}
