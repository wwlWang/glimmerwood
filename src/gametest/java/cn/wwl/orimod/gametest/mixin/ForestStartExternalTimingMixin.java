package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestStartTiming;
import cn.wwl.orimod.world.RuinDecision;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import java.util.Optional;
import java.util.function.Supplier;

/** B-only explanatory profile. Never configured in CPU A/B acceptance runs. */
@Mixin(targets="cn.wwl.orimod.world.ForestStructureReservations",remap=false)
abstract class ForestStartExternalTimingMixin {
    @WrapMethod(method="rejection")
    private String measure(Structure.GenerationContext c,RuinDecision own,Operation<String> original){
        if(!ForestStartTiming.active())return original.call(c,own);
        long start=System.nanoTime();try{return original.call(c,own);}finally{ForestStartTiming.external(System.nanoTime()-start);}
    }
    @WrapOperation(method="rejection",at=@At(value="INVOKE",target="Lcn/wwl/orimod/world/RuinDecisionCache;get(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;",ordinal=1))
    private Object lookup(@Coerce Object cache,Object key,Supplier<?> factory,Operation<Object> original){
        if(!ForestStartTiming.active())return original.call(cache,key,factory);
        ForestStartTiming.enterQualification(key);long start=System.nanoTime();boolean valid=false;
        try{Object value=original.call(cache,key,factory);valid=((Optional<?>)value).isPresent();return value;}
        finally{ForestStartTiming.leaveQualification(System.nanoTime()-start,valid);}
    }
    @WrapMethod(method="qualify")
    private static Optional<?> computed(Structure.GenerationContext c,Holder<StructureSet> set,ChunkPos cp,Operation<Optional<?>> original){
        if(ForestStartTiming.active())ForestStartTiming.coldQualification(set.unwrapKey().orElseThrow().location().toString(),cp.x,cp.z);
        return original.call(c,set,cp);
    }
}
