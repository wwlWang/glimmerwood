package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.GameTestDiskBackpressure;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts on the owning I/O thread; no unsafe cross-thread iteration of pendingWrites. */
@Mixin(IOWorker.class)
public abstract class GameTestIoMetricsMixin implements GameTestDiskBackpressure.Metrics {
    @Shadow @Final private Map<ChunkPos, ?> pendingWrites;
    @Unique private volatile int orimod$pending;
    @Unique private volatile int orimod$peak;
    @Unique private final AtomicInteger orimod$windowPeak = new AtomicInteger();

    // Vanilla calls this after every foreground command (including store) and
    // every background removal. Thus an enqueue burst's peak cannot be missed.
    @Inject(method = "tellStorePending", at = @At("HEAD"))
    private void orimod$recordQueue(CallbackInfo ci) {
        int size = pendingWrites.size();
        orimod$pending = size;
        orimod$peak = Math.max(orimod$peak, size);
        orimod$windowPeak.accumulateAndGet(size, Math::max);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void orimod$finalQueue(CallbackInfo ci) {
        System.out.println("GAMETEST_DISK_FINAL storage=" + ((IOWorker) (Object) this).storageInfo()
                + " pending=" + orimod$pending + " totalPeak=" + orimod$peak);
    }

    public int orimod$pendingWrites() { return orimod$pending; }
    public int orimod$peakPendingWrites() { return orimod$peak; }
    public int orimod$takeWindowPeak() { return orimod$windowPeak.getAndSet(orimod$pending); }
}
