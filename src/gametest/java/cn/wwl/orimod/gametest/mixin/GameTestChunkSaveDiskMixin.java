package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.GameTestDiskBackpressure;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RETURN is after ChunkStorage.write and outside vanilla save's catch block. */
@Mixin(ChunkMap.class)
public abstract class GameTestChunkSaveDiskMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="save",at=@At("RETURN"))
    private void orimod$boundSubmittedNbt(ChunkAccess chunk,CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) GameTestDiskBackpressure.submitted(level);
    }
}
