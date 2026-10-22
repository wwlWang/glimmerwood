package cn.wwl.orimod.progression;

import cn.wwl.orimod.combat.Flash;
import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.network.AnimationActionPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** An awakened baseline skill, derived from AWAKENED rather than changing the purchased ability mask. */
public final class Regenerate {
    private Regenerate() {}
    public static final class Session {
        public long until = Long.MIN_VALUE, began = Long.MIN_VALUE, lastTick = Long.MIN_VALUE;
        public boolean interrupted;
    }
    public static boolean active(ServerPlayer p) { return p.getData(SpiritData.ENERGY_STATE).regenerate.began != Long.MIN_VALUE; }
    public static String refusal(ServerPlayer p) {
        var s = p.getData(SpiritData.ENERGY_STATE);
        boolean movement = switch (p.getData(SpiritData.MOVEMENT).mode) {
            case DASH, BASH_AIM, GRAPPLE, STOMP -> true;
            default -> false;
        };
        return RecoveryRules.refusal(SpiritCombat.available(p) && !Restoration.active(p), p.getHealth(), p.getMaxHealth(),
                SpiritProgression.energy(p), movement || p.isUsingItem() || !p.getData(SpiritData.COMBAT).mode.equals("idle")
                        || Flash.active(p) || p.level().getGameTime() < s.chargeFlameUntil, s.regenerate.interrupted);
    }
    public static boolean keepAlive(ServerPlayer p) {
        var s = p.getData(SpiritData.ENERGY_STATE).regenerate;
        if (refusal(p) != null) { cancel(p, false); return false; }
        long now = p.level().getGameTime();
        if (s.began == Long.MIN_VALUE || now >= s.until) s.began = now;
        s.until = now + RecoveryTuning.LEASE_TICKS;
        AnimationActionPayload.spell(p, AnimationActionPayload.Action.CAST_HOLD);
        return true;
    }
    public static void tick(ServerPlayer p) {
        var s = p.getData(SpiritData.ENERGY_STATE).regenerate;
        long now = p.level().getGameTime();
        if (!active(p) || s.lastTick == now) return;
        s.lastTick = now;
        if (now >= s.until || refusal(p) != null) { cancel(p, false); return; }
        if (now - s.began < RecoveryTuning.CHANNEL_TICKS) return;
        if (!SpiritProgression.spendEnergy(p, RecoveryTuning.CAST_COST)) { cancel(p, false); return; }
        p.heal(RecoveryTuning.CAST_HEAL);
        AnimationActionPayload.spell(p, AnimationActionPayload.Action.CAST_RELEASE);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .4f, 1.3f);
        s.began = now;
        if (p.getHealth() >= p.getMaxHealth()) cancel(p, false);
    }
    /** Release clears the damage latch. Cancellation/late keepalive must not automatically restart after a hit. */
    public static void release(ServerPlayer p) { cancel(p, false); p.getData(SpiritData.ENERGY_STATE).regenerate.interrupted = false; }
    public static void cancel(ServerPlayer p, boolean damaged) {
        var s = p.getData(SpiritData.ENERGY_STATE).regenerate;
        if (active(p)) {
            AnimationActionPayload.spell(p, AnimationActionPayload.Action.CAST_STOP);
            if (damaged) s.interrupted = true;
        }
        s.began = s.until = Long.MIN_VALUE;
    }
}
