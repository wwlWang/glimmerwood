package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestLavaEvidence;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NoiseBasedChunkGenerator.class)
abstract class ForestLavaCarversMixin {
    @Inject(method="applyCarvers",at=@At("HEAD"))
    private void before(WorldGenRegion level,long seed,RandomState random,BiomeManager biome,StructureManager manager,ChunkAccess chunk,GenerationStep.Carving step,CallbackInfo ci){ForestLavaEvidence.carvers(level,chunk,true);}
    @Inject(method="applyCarvers",at=@At("RETURN"))
    private void after(WorldGenRegion level,long seed,RandomState random,BiomeManager biome,StructureManager manager,ChunkAccess chunk,GenerationStep.Carving step,CallbackInfo ci){ForestLavaEvidence.carvers(level,chunk,false);}
}
