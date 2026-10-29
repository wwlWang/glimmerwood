package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestCheapExternal;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerChunkCache.class)
abstract class ForestCheapChunkBoundaryMixin {
    @Inject(method="getChunk",at=@At("HEAD"))
    private void orimod$chunk(CallbackInfoReturnable<?> cir){ForestCheapExternal.count(3);}
}
