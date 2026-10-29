package cn.wwl.orimod.gametest.mixin;
import cn.wwl.orimod.gametest.ForestLavaEvidence;
import net.minecraft.world.ticks.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin({ProtoChunkTicks.class,LevelChunkTicks.class})
abstract class ForestLavaScheduleMixin<T> {
    @SuppressWarnings("unchecked")
    @Inject(method="schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V",at=@At("HEAD"))
    private void before(ScheduledTick<T> tick,CallbackInfo ci){if(ForestLavaEvidence.ENABLED)ForestLavaEvidence.schedule("schedule_before_"+getClass().getSimpleName(),tick,((TickContainerAccess<T>)(Object)this).hasScheduledTick(tick.pos(),tick.type()));}
    @SuppressWarnings("unchecked")
    @Inject(method="schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V",at=@At("RETURN"))
    private void after(ScheduledTick<T> tick,CallbackInfo ci){if(ForestLavaEvidence.ENABLED)ForestLavaEvidence.schedule("schedule_after_"+getClass().getSimpleName(),tick,((TickContainerAccess<T>)(Object)this).hasScheduledTick(tick.pos(),tick.type()));}
}
