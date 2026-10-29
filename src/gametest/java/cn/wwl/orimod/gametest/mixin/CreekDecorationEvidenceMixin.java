package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.CreekGuardEvidence;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class CreekDecorationEvidenceMixin {
    @Inject(method="applyBiomeDecoration",at=@At("HEAD"))
    private void orimod$begin(WorldGenLevel level,ChunkAccess chunk,StructureManager manager,CallbackInfo ci){CreekGuardEvidence.decoration(level,chunk.getPos(),true);cn.wwl.orimod.gametest.ForestLavaEvidence.decoration(level,chunk.getPos(),true);}
    @Inject(method="applyBiomeDecoration",at=@At("RETURN"))
    private void orimod$end(WorldGenLevel level,ChunkAccess chunk,StructureManager manager,CallbackInfo ci){CreekGuardEvidence.decoration(level,chunk.getPos(),false);cn.wwl.orimod.gametest.ForestLavaEvidence.decoration(level,chunk.getPos(),false);}
}
