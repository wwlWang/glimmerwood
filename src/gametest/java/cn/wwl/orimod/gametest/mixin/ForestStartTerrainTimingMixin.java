package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestStartTiming;
import cn.wwl.orimod.world.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=RuinStructure.class,remap=false)
abstract class ForestStartTerrainTimingMixin {
    @WrapMethod(method="evaluateStages")
    private RuinStructure.SiteResult measure(Structure.GenerationContext c,int version,boolean owner,Operation<RuinStructure.SiteResult> original){
        if(ForestStartTiming.neighbour()){
            long start=System.nanoTime();String reason="EXCEPTION";
            try{var result=original.call(c,version,owner);reason=result.reason();return result;}
            finally{ForestStartTiming.neighbourTerrain(((RuinStructure)(Object)this).kind.id,reason,System.nanoTime()-start);}
        }
        if(((RuinStructure)(Object)this).kind!=RuinKind.FOREST||!ForestStartTiming.ownForest())return original.call(c,version,owner);
        long nested=ForestStartTiming.r2Nanos(),start=System.nanoTime();
        try{return original.call(c,version,owner);}finally{ForestStartTiming.forest(System.nanoTime()-start-(ForestStartTiming.r2Nanos()-nested));}
    }
}
