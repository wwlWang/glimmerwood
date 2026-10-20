package cn.wwl.orimod.progression;

import cn.wwl.orimod.combat.CombatTargets;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.PracticeTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.ArrayDeque;

/** R99: WotW-inspired harvest and retaliation shards; server-owned effects with session-only limits. */
public final class ShardEffects {
    public static final float LIFE_PER_LIGHT = .5f, LIFE_PER_SECOND = 1, ENERGY_PER_LIGHT = 2,
            THORN_DAMAGE = 2, ULTRA_BASH_DAMAGE = 4;
    public static final int HARVEST_WINDOW_TICKS = 20, THORN_COOLDOWN_TICKS = 10, FOURTH_SLOT_ABILITIES = 10;
    public static final double THORN_RANGE = 3, BASH_LAUNCH_MULTIPLIER = 1.2, PROJECTILE_SPEED_MULTIPLIER = 1.3;
    private ShardEffects() {}

    /** R99: at most one health point in any rolling twenty-tick window, including boundary bursts. */
    public static final class HarvestWindow {
        private record Heal(long tick, float amount) {}
        private final ArrayDeque<Heal> heals = new ArrayDeque<>();
        public float claim(long now, int light, float missingHealth) {
            if (!heals.isEmpty() && now < heals.getLast().tick) heals.clear();
            while (!heals.isEmpty() && now - heals.getFirst().tick >= HARVEST_WINDOW_TICKS) heals.removeFirst();
            if (light <= 0 || !Float.isFinite(missingHealth) || missingHealth <= 0) return 0;
            float used = 0;
            for (var heal : heals) used += heal.amount;
            float amount = Math.max(0, Math.min(Math.min(light * LIFE_PER_LIGHT, missingHealth), LIFE_PER_SECOND - used));
            if (amount > 0) {
                float atTick = !heals.isEmpty() && heals.getLast().tick == now ? heals.removeLast().amount : 0;
                heals.addLast(new Heal(now, atTick + amount));
            }
            return amount;
        }
    }

    /** Only consumed Spirit Light items call this; quest rewards and command currency grants do not. */
    public static void harvested(ServerPlayer player, int light) {
        if (light <= 0 || !player.isAlive()) return;
        if (Shard.LIFE_HARVEST.equipped(player)) {
            float heal = player.getData(SpiritData.ENERGY_STATE).lifeHarvest.claim(player.level().getGameTime(), light,
                    player.getMaxHealth() - player.getHealth());
            if (heal > 0) player.heal(heal);
        }
        if (Shard.ENERGY_HARVEST.equipped(player))
            player.setData(SpiritData.ENERGY, Math.min(SpiritProgression.maxEnergy(player), SpiritProgression.energy(player) + light * ENERGY_PER_LIGHT));
    }

    public static boolean thornMelee(boolean directAttacker, boolean livingAttacker, boolean projectile,
                                     boolean reflected, float damage, double distanceSquared) {
        return directAttacker && livingAttacker && !projectile && !reflected && Float.isFinite(damage) && damage > 0
                && distanceSquared >= 0 && distanceSquared <= THORN_RANGE * THORN_RANGE;
    }
    public static boolean thornReady(long now, long last) {
        return last == Long.MIN_VALUE || now < last || now - last >= THORN_COOLDOWN_TICKS;
    }

    public static void reflect(ServerPlayer player, DamageSource source, float damage) {
        Entity attacker = source.getEntity();
        if (!Shard.THORN.equipped(player) || attacker == null || attacker == player
                || !thornMelee(source.getDirectEntity() == attacker, attacker instanceof LivingEntity,
                source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE),
                source.is(DamageTypes.THORNS), damage, player.distanceToSqr(attacker))) return;
        long now = player.level().getGameTime();
        var hits = player.getData(SpiritData.ENERGY_STATE).thornHits;
        hits.entrySet().removeIf(entry -> thornReady(now, entry.getValue()));
        if (hits.containsKey(attacker.getUUID())) return;
        // Claim before hurt(): reflected damage dispatches its own synchronous Post event.
        hits.put(attacker.getUUID(), now);
        attacker.hurt(player.damageSources().thorns(player), THORN_DAMAGE);
    }

    public static boolean ultraBashTarget(ServerPlayer player, Entity target) {
        return target instanceof LivingEntity living && target instanceof Enemy && !(target instanceof PracticeTarget)
                && CombatTargets.canHit(player, living);
    }
    public static void bashHit(ServerPlayer player, Entity target) {
        if (Shard.ULTRA_BASH.equipped(player) && ultraBashTarget(player, target))
            target.hurt(player.damageSources().indirectMagic(player, player), ULTRA_BASH_DAMAGE);
    }
}
