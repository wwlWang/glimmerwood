package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.DoorOpening;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Snapshot before the first air update removes a cell. Chunk unloads/resyncs do not animate. */
@Mixin(LevelChunk.class)
public abstract class KeystoneOpeningMixin {
    @Inject(method="setBlockState",at=@At("HEAD"))
    private void orimod$opening(BlockPos pos,BlockState next,boolean moving,CallbackInfoReturnable<BlockState> cir) {
        DoorOpening.removing(((LevelChunk)(Object)this).getLevel(),pos,next);
    }
}
