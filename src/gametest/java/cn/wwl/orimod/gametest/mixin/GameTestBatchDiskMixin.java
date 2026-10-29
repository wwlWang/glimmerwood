package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.GameTestDiskBackpressure;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestBatch;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestRunner.class)
public abstract class GameTestBatchDiskMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow private ImmutableList<GameTestBatch> batches;

    /** Previous batch has finished; next batch's fixtures/timing have not begun. */
    @Inject(method = "runBatch", at = @At("HEAD"))
    private void orimod$drainPreviousBatch(int index, CallbackInfo ci) {
        GameTestDiskBackpressure.batch(index < batches.size() ? batches.get(index).name() : "tests-complete");
        GameTestDiskBackpressure.unloadAndDrain(level, "batch-boundary-" + index);
    }
}
