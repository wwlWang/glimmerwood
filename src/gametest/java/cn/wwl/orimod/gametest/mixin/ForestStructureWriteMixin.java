package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestStructureEvidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Counts even unchanged/rejected attempts; observes once at the structure's WorldGenLevel API. */
@Mixin(WorldGenRegion.class)
abstract class ForestStructureWriteMixin {
    @WrapMethod(method="setBlock")
    private boolean write(BlockPos pos,BlockState state,int flags,int depth,Operation<Boolean> original){
        if(!ForestStructureEvidence.recording())return original.call(pos,state,flags,depth);
        var region=(WorldGenRegion)(Object)this;
        if(!region.hasChunk(pos.getX()>>4,pos.getZ()>>4)){ForestStructureEvidence.unavailable(pos,"Write outside readable WorldGenRegion");return original.call(pos,state,flags,depth);}
        var before=region.getBlockState(pos);ForestStructureEvidence.enterWrite(pos);
        try{boolean result=original.call(pos,state,flags,depth);
            ForestStructureEvidence.write(pos,before,state,region.getBlockState(pos));return result;
        }finally{ForestStructureEvidence.exitWrite();}
    }
}
