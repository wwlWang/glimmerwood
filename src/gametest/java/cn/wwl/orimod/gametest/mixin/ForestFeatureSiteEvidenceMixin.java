package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.ForestFeatureSiteEvidence;
import cn.wwl.orimod.world.FeatureSitePiece;
import cn.wwl.orimod.world.FeatureSiteStructure;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FeatureSitePiece.class)
abstract class ForestFeatureSiteEvidenceMixin {
    @WrapMethod(method="postProcess")
    private void observe(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox bounds,ChunkPos chunk,BlockPos pivot,Operation<Void> original){
        ForestFeatureSiteEvidence.begin((FeatureSitePiece)(Object)this,chunk);boolean normal=false;
        try{original.call(level,manager,generator,random,bounds,chunk,pivot);normal=true;}finally{ForestFeatureSiteEvidence.end(normal);}
    }
    @WrapOperation(method="postProcess",at=@At(value="INVOKE",target="Lcn/wwl/orimod/world/FeatureSitePiece;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/core/BlockPos;J)Z"))
    private boolean attempt(FeatureSitePiece piece,WorldGenLevel level,ChunkGenerator generator,BlockPos at,long seed,Operation<Boolean> original){
        ForestFeatureSiteEvidence.attempt();boolean result=original.call(piece,level,generator,at,seed);ForestFeatureSiteEvidence.placed(result);return result;
    }
    @WrapOperation(method="postProcess",at=@At(value="INVOKE",target="Lcn/wwl/orimod/world/FeatureSiteStructure$Site;declineReason(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;J)Ljava/lang/String;"))
    private String reason(FeatureSiteStructure.Site site,WorldGenLevel level,BlockPos at,long seed,Operation<String> original){
        String result=original.call(site,level,at,seed);ForestFeatureSiteEvidence.reason(result);return result;
    }
}
