package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestLavaEvidence;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ChunkGenerator.class)
abstract class ForestLavaStagesMixin {
    @WrapOperation(method="applyBiomeDecoration",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/WorldgenRandom;setFeatureSeed(JII)V"))
    private void step(WorldgenRandom random,long seed,int index,int step,Operation<Void> original){
        ForestLavaEvidence.step(step);original.call(random,seed,index,step);
    }
}
