package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.RuinOuterTimer;
import cn.wwl.orimod.world.RuinPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RuinPiece.class)
abstract class RuinOuterTimerMixin {
    @Inject(method="postProcess",at=@At("HEAD"))
    private void orimod$start(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,
            BoundingBox clip,ChunkPos chunk,BlockPos pivot,CallbackInfo ci){
        cn.wwl.orimod.gametest.CreekWaterSourceTrace.before((RuinPiece)(Object)this,level,clip);
        cn.wwl.orimod.gametest.CreekForeignTerrainTrace.begin(level,chunk);
        RuinOuterTimer.begin();
    }
    @Inject(method="postProcess",at=@At("RETURN"))
    private void orimod$end(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,
            BoundingBox clip,ChunkPos chunk,BlockPos pivot,CallbackInfo ci){RuinOuterTimer.end((RuinPiece)(Object)this,chunk);cn.wwl.orimod.gametest.CreekForeignTerrainTrace.end(level);}
}
