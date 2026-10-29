package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestStructureEvidence;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChunkAccess.class)
abstract class ForestStructureSectionsMixin {
    @Shadow @Final protected ChunkPos chunkPos;
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Shadow @Final protected LevelChunkSection[] sections;
    @Inject(method="<init>",at=@At("RETURN"))
    private void origins(CallbackInfo ci){
        // No virtual ChunkAccess getters here: ImposterProtoChunk.wrapped is not initialized yet.
        ForestStructureEvidence.registerSections(chunkPos,levelHeightAccessor.getMinSection(),sections);
    }
}
