package cn.wwl.orimod.combat;

import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.spirit.SpiritData;
import java.util.Comparator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * R37 companion Spirit Flame (after Sein in Ori and the Blind Forest, with an original look): while in spirit
 * form the companion light automatically strikes the nearest visible hostile within range on a cooldown.
 * It costs no energy, never targets players, allies, passive mobs or practice crystals, and credits the
 * player so Spirit Light drops as for any other kill.
 */
public final class SpiritFlame {
    public static final double RANGE = 6;
    public static final int COOLDOWN_TICKS = 14;
    public static final float DAMAGE = 2;

    /** Pure rule: may the companion fire this tick. */
    public static boolean ready(boolean spirit, boolean alive, boolean spectator, long now, long readyAt) {
        return spirit && alive && !spectator && now >= readyAt;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var state = player.getData(SpiritData.ENERGY_STATE);
        long now = player.level().getGameTime();
        if (!ready(SpiritData.isSpirit(player) && SpiritData.isAwakened(player), player.isAlive(), player.isSpectator(), now, state.flameReadyAt)) return;
        var target = target(player);
        if (target == null) return;
        state.flameReadyAt = now + COOLDOWN_TICKS;
        fire(player, target);
    }

    public static LivingEntity target(ServerPlayer player) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), e -> e instanceof Enemy && e.isAlive()
                        && !e.isInvisible() && !player.isAlliedTo(e) && player.distanceToSqr(e) <= RANGE * RANGE
                        && SpiritTargets.visible(player, e.getBoundingBox().getCenter()))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    public static boolean fire(ServerPlayer player, LivingEntity target) {
        var at = target.getBoundingBox().getCenter();
        SpiritFx.emit(player.serverLevel(), SpiritFxKind.SPIRIT_FLAME, at, player);
        boolean hit = target.hurt(player.damageSources().indirectMagic(player, player), DAMAGE);
        fired++;
        return hit;
    }

    /** Diagnostics: flames fired since server start. */
    public static int fired;

    private SpiritFlame() {}
}
