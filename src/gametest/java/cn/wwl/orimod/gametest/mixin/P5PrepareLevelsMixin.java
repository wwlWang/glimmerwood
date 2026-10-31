package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.P5ChunkPerformanceAudit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class P5PrepareLevelsMixin {
    @Unique private long orimod$p5PrepareStart;
    @Inject(method="prepareLevels",at=@At("HEAD"))
    private void orimod$start(ChunkProgressListener listener,CallbackInfo ci){if(P5ChunkPerformanceAudit.enabled())orimod$p5PrepareStart=System.nanoTime();}
    @Inject(method="prepareLevels",at=@At("RETURN"))
    private void orimod$end(ChunkProgressListener listener,CallbackInfo ci){if(P5ChunkPerformanceAudit.enabled())P5ChunkPerformanceAudit.prepared((System.nanoTime()-orimod$p5PrepareStart)/1e6);}
}
