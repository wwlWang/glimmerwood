package cn.wwl.orimod.combat;

import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * R59 Charge Flame (BF): hold the key to gather flame, release to burst it around Ori. Hits every valid target
 * within {@link #RADIUS}, pushes them away and cleanses gloom water nearby (R61). The client measures the hold;
 * releasing early does nothing.
 */
public final class ChargeFlame {
    public static final float COST = 10, DAMAGE = 8;
    public static final double RADIUS = 3.5;
    public static final int CHARGE_TICKS = 12, COOLDOWN_TICKS = 30;

    public static String refusal(boolean spirit, boolean unlocked, float energy, long now, long readyAt) {
        if (!spirit) return "not_spirit";
        if (!unlocked) return "locked";
        if (now < readyAt) return "cooldown";
        if (energy < COST) return "energy";
        return null;
    }

    /** Release a charged flame. @return targets hit, or -1 if refused. */
    public static int release(ServerPlayer player) {
        long now = player.level().getGameTime();
        var state = player.getData(SpiritData.ENERGY_STATE);
        String refusal = refusal(SpiritData.isSpirit(player), Ability.CHARGE_FLAME.unlocked(player), SpiritProgression.energy(player), now, state.chargeFlameReadyAt);
        if (refusal != null) {
            if (!refusal.equals("cooldown")) player.displayClientMessage(Component.translatable("message.orimod.charge_flame." + refusal), true);
            return -1;
        }
        if (!SpiritProgression.spendEnergy(player, COST)) return -1;
        state.chargeFlameReadyAt = now + COOLDOWN_TICKS;
        var level = (ServerLevel) player.level();
        Vec3 center = player.position().add(0, .8, 0);
        int hits = 0;
        for (var target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
                e -> e != player && e.distanceToSqr(center) <= RADIUS * RADIUS * 1.2 && CombatTargets.canHit(player, e))) {
            if (target.hurt(player.damageSources().indirectMagic(player, player), DAMAGE)) {
                hits++;
                Vec3 push = target.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(.6);
                target.push(push.x, .3, push.z); target.hurtMarked = true;
            }
        }
        cn.wwl.orimod.block.GloomWaterBlock.cleanse(level, player.blockPosition(), (int) Math.ceil(RADIUS) + 1);
        cn.wwl.orimod.fx.SpiritFx.emit(level, cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, player.getX(), player.getY() + .1, player.getZ(), null);
        cn.wwl.orimod.fx.SpiritFx.emit(level, cn.wwl.orimod.fx.SpiritFxKind.FORM_SWITCH, player.getX(), player.getY(), player.getZ(), null);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, .8f, 1.2f);
        return hits;
    }

    private ChargeFlame() {}
}
