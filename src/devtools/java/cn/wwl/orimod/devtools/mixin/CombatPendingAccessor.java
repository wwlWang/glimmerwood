package cn.wwl.orimod.devtools.mixin;

import cn.wwl.orimod.client.SpiritCombatClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=SpiritCombatClient.class,remap=false)
public interface CombatPendingAccessor {
    @Accessor("awaitingAttackUntil") static int orimod$awaitingAttackUntil() { throw new AssertionError("mixin"); }
}
