package cn.wwl.orimod.mixin;

import cn.wwl.orimod.world.GreatTreeSite;
import cn.wwl.orimod.world.GreatTreeStructure;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla searches only rings/random-spread. Add the persisted site, leaving vanilla command formatting intact. */
@Mixin(ChunkGenerator.class)
public abstract class GreatTreeLocateMixin {
    @Inject(method="findNearestMapStructure",at=@At("RETURN"),cancellable=true)
    private void orimod$greatTree(ServerLevel level, HolderSet<Structure> structures, BlockPos from,int radius,boolean skipKnown,
                                 CallbackInfoReturnable<Pair<BlockPos,Holder<Structure>>> cir){
        if(level.dimension()!=Level.OVERWORLD)return;
        for(var holder:structures)if(holder.value() instanceof GreatTreeStructure){
            var data=GreatTreeSite.get(level);var site=data.site();if(site==null || skipKnown&&data.begun())return;
            var previous=cir.getReturnValue();
            if(previous==null || from.distSqr(site.origin())<from.distSqr(previous.getFirst()))cir.setReturnValue(Pair.of(site.origin(),holder));
            return;
        }
    }
}
