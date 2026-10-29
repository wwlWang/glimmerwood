package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestLavaEvidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Includes BulkSectionAccess/OreFeature; observed sections acquire immutable world origins at CARVERS. */
@Mixin(LevelChunkSection.class)
abstract class ForestSectionEvidenceMixin {
    @WrapMethod(method="setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;")
    private BlockState record(int x,int y,int z,BlockState state,boolean locks,Operation<BlockState> original){
        var section=(LevelChunkSection)(Object)this;var at=ForestLavaEvidence.sectionPosition(section,x,y,z);
        if(at==null)return original.call(x,y,z,state,locks);
        var before=section.getBlockState(x,y,z);var result=original.call(x,y,z,state,locks);
        ForestLavaEvidence.sectionWrite(at,before,state,section.getBlockState(x,y,z));return result;
    }
}
