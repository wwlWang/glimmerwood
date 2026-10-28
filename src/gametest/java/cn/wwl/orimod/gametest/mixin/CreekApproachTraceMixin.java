package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.CreekForeignTerrainTrace;
import cn.wwl.orimod.world.RuinApproachPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(RuinApproachPiece.class)
abstract class CreekApproachTraceMixin {
    @Inject(method="postProcess",at=@At("HEAD"))
    private void before(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,
            BoundingBox clip,ChunkPos chunk,BlockPos pivot,CallbackInfo ci){CreekForeignTerrainTrace.begin(level,chunk);}
    @Inject(method="postProcess",at=@At("RETURN"))
    private void after(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,
            BoundingBox clip,ChunkPos chunk,BlockPos pivot,CallbackInfo ci){CreekForeignTerrainTrace.end(level);}
}
