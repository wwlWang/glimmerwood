package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.fx.BloomSparkClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client block updates only. Initial chunk palette deserialization does not call setBlockState. */
@Mixin(LevelChunk.class)
public abstract class RestorationLeavesMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void orimod$bloom(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> cir){
        BloomSparkClient.changed(((LevelChunk)(Object)this).getLevel(),pos,cir.getReturnValue(),state);
    }
}
