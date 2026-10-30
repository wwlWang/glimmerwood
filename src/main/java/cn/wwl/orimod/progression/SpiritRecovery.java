package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class SpiritRecovery {
    private SpiritRecovery() {}
    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && (event.getNewDamage() > 0
                || event.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION) > 0)) {
            p.getData(SpiritData.ENERGY_STATE).recoveryHurtAt = p.level().getGameTime();
            Regenerate.cancel(p, true);
        }
    }
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Regenerate.release(p);
    }
    public static void tick(ServerPlayer p) {
        long now = p.level().getGameTime(); var s = p.getData(SpiritData.ENERGY_STATE);
        if (s.recoveryHurtAt == Long.MIN_VALUE) s.recoveryHurtAt = now;
        Regenerate.tick(p);
        if (s.recoveryLastTick == now) return;
        s.recoveryLastTick = now;
        if(BlightExposure.suppress(p))return;
        if (now % 4 == 0) {
            float amount = RecoveryRules.energy(SpiritProgression.energy(p), SpiritProgression.maxEnergy(p), now - s.lastSpent,
                    Regenerate.active(p) || cn.wwl.orimod.combat.Flash.active(p), Shard.RECOVERY.equipped(p), .2f);
            if (amount > 0) p.setData(SpiritData.ENERGY, SpiritProgression.energy(p) + amount);
        }
        if (now % 20 == 0) {
            float amount = RecoveryRules.life(p.getHealth(), p.getMaxHealth(), SpiritData.isSpirit(p) && !p.isSpectator()
                    && p.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION), now - s.recoveryHurtAt, 1);
            if (amount > 0) p.heal(amount);
        }
    }
}
