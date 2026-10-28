package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.LocateCandidateEvent;

import cn.wwl.orimod.world.RuinStructure;
import com.mojang.datafixers.util.Pair;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-source-only observation of vanilla locate candidates, including cached rejects. */
@Mixin(ChunkGenerator.class)
abstract class LocateCandidateMixin {
    private static final ThreadLocal<LocateCandidateEvent> orimod$active=new ThreadLocal<>();
    @Inject(method="getStructureGeneratingAt",at=@At("HEAD"))
    private static void orimod$begin(Set<Holder<Structure>> holders,LevelReader level,StructureManager manager,
            boolean skip,StructurePlacement placement,ChunkPos pos,CallbackInfoReturnable<Pair<BlockPos,Holder<Structure>>> cir){
        if(!Boolean.getBoolean("orimod.profileRuinSiting"))return;
        for(var h:holders)if(h.value() instanceof RuinStructure ruin){
            var e=new LocateCandidateEvent();e.kind=ruin.kind.id;e.x=pos.x;e.z=pos.z;e.begin();orimod$active.set(e);break;
        }
    }
    @Inject(method="getStructureGeneratingAt",at=@At("RETURN"))
    private static void orimod$end(Set<Holder<Structure>> holders,LevelReader level,StructureManager manager,
            boolean skip,StructurePlacement placement,ChunkPos pos,CallbackInfoReturnable<Pair<BlockPos,Holder<Structure>>> cir){
        var e=orimod$active.get();if(e!=null){orimod$active.remove();e.end();e.found=cir.getReturnValue()!=null;e.commit();}
    }
}
