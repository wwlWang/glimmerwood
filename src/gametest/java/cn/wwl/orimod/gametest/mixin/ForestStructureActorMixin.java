package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestStructureEvidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Actor identity is independent of the package of a helper making the final setBlock call. */
@Mixin(StructureStart.class)
abstract class ForestStructureActorMixin {
    @WrapMethod(method="placeInChunk")
    private void start(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox bounds,ChunkPos chunk,Operation<Void> original){
        if(!ForestStructureEvidence.ENABLED){original.call(level,manager,generator,random,bounds,chunk);return;}
        ForestStructureEvidence.begin(level,(StructureStart)(Object)this,null);
        try{original.call(level,manager,generator,random,bounds,chunk);}finally{ForestStructureEvidence.end();}
    }
    @WrapOperation(method="placeInChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/structure/StructurePiece;postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V"))
    private void piece(StructurePiece piece,WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox bounds,ChunkPos chunk,BlockPos pivot,Operation<Void> original){
        if(!ForestStructureEvidence.ENABLED){original.call(piece,level,manager,generator,random,bounds,chunk,pivot);return;}
        ForestStructureEvidence.begin(level,(StructureStart)(Object)this,piece);
        try{original.call(piece,level,manager,generator,random,bounds,chunk,pivot);}finally{ForestStructureEvidence.end();}
    }
}
