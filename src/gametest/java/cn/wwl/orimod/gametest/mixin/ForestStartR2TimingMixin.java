package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestStartTiming;
import cn.wwl.orimod.world.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.ChunkPos;
import java.util.Optional;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=RuinSitingSites.class,remap=false)
abstract class ForestStartR2TimingMixin {
    @WrapMethod(method="yieldBounds")
    private static RuinSitingSites.Yield measure(Structure.GenerationContext c,List<RuinDecision.Box> own,Operation<RuinSitingSites.Yield> original){
        if(!ForestStartTiming.active())return original.call(c,own);
        ForestStartTiming.enterR2();long start=System.nanoTime();
        try{return original.call(c,own);}finally{ForestStartTiming.leaveR2(System.nanoTime()-start);}
    }
    @WrapMethod(method="collisionSelection")
    private static Optional<RuinDecision> neighbour(Structure.GenerationContext c,ChunkPos cp,List<RuinDecision.Box> own,Operation<Optional<RuinDecision>> original){
        if(!ForestStartTiming.active())return original.call(c,cp,own);
        ForestStartTiming.enterPrediction(cp.x,cp.z);long start=System.nanoTime();String selected="EXCEPTION";
        try{var result=original.call(c,cp,own);selected=result.map(s->s.kind().id).orElse("NONE");return result;}
        finally{ForestStartTiming.leavePrediction(System.nanoTime()-start,selected);}
    }
}
