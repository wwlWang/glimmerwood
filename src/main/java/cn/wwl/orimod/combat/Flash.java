package cn.wwl.orimod.combat;

import cn.wwl.orimod.block.GloomWaterBlock;
import cn.wwl.orimod.block.SpiritLanternBlock;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.fx.SpiritFxLifecycle;
import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** R98: WotW Flash adapted as a leased, server-owned light that drains energy, dazzles gloom and suppresses natural spawns. */
public final class Flash {
    // Client ticks can arrive in frame-sized batches. Explicit release ends Flash;
    // this longer lease only bounds how long lost input can keep it alive.
    public static final int KEEPALIVE_TICKS = 5, LEASE_TICKS = 20, COOLDOWN_TICKS = 20,
            GLOOM_INTERVAL = 10, BOSS_INTERVAL = 20, UNDEAD_INTERVAL = 20, WEAKNESS_TICKS = 40,
            DAZZLE_TICKS = 12, LANTERN_INTERVAL = 10, LANTERN_TICKS = 40, CLEANSE_START = 40, CLEANSE_INTERVAL = 20,
            CLEANSE_RADIUS = 3;
    public static final float START_ENERGY = 5, COST_PER_TICK = .3f, GLOOM_DAMAGE = 1, BOSS_DAMAGE = 1, UNDEAD_DAMAGE = .5f;
    public static final double RADIUS = 7, SPAWN_RADIUS = 16;
    private static final Set<ServerPlayer> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private Flash() {}

    public static String refusal(boolean spirit, boolean unlocked, float energy, long now, long readyAt,
                                 MoveMode movement, boolean charging, boolean continuing) {
        if (!spirit) return "not_spirit";
        if (!unlocked) return "locked";
        if (now < readyAt) return "cooldown";
        if (!Float.isFinite(energy) || (continuing ? energy <= 0 : energy < START_ENERGY)) return "energy";
        if (movement == MoveMode.BASH_AIM || movement == MoveMode.DASH || movement == MoveMode.GRAPPLE || movement == MoveMode.STOMP) return "movement";
        if (charging) return "charging";
        return null;
    }

    public static boolean leased(long now, long until) { return now < until; }
    public static boolean lanternDue(long age) { return age > 0 && (age - 1) % LANTERN_INTERVAL == 0; }
    public static boolean cleanseDue(long age) { return age >= CLEANSE_START && (age - CLEANSE_START) % CLEANSE_INTERVAL == 0; }
    public static boolean suppresses(boolean monster, MobSpawnType type, boolean sameLevel, boolean active, double distanceSquared) {
        return monster && type == MobSpawnType.NATURAL && sameLevel && active && distanceSquared >= 0 && distanceSquared <= SPAWN_RADIUS * SPAWN_RADIUS;
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.contains(player) && leased(player.level().getGameTime(), player.getData(SpiritData.ENERGY_STATE).flashUntil)
                && SpiritCombat.available(player);
    }
    /** R101 uses a wider defense radius; R98's natural-spawn rule remains unchanged. */
    public static boolean protectsSpawn(ServerLevel level, Vec3 point, double radius) {
        for (var player : ACTIVE)
            if (player.serverLevel() == level && active(player) && player.distanceToSqr(point) <= radius * radius) return true;
        return false;
    }
    public static int activeCount() { return ACTIVE.size(); }

    private static String refusal(ServerPlayer player, boolean continuing) {
        var s = player.getData(SpiritData.ENERGY_STATE);
        return refusal(SpiritData.isSpirit(player), Ability.FLASH.unlocked(player), SpiritProgression.energy(player),
                cn.wwl.orimod.progression.SpiritCooldowns.now(player), player.getData(SpiritData.COOLDOWNS).flashReadyAt, player.getData(SpiritData.MOVEMENT).mode,
                player.level().getGameTime() < s.chargeFlameUntil, continuing);
    }

    private static boolean weaponBusy(ServerPlayer player) {
        return !player.getData(SpiritData.COMBAT).mode.equals("idle") || player.isUsingItem();
    }

    /** Input only renews a short lease; it never determines targets, damage or energy. */
    public static boolean keepAlive(ServerPlayer player) {
        long now = player.level().getGameTime();
        var s = player.getData(SpiritData.ENERGY_STATE);
        if (ACTIVE.contains(player) && !active(player)) stop(player);
        boolean continuing = ACTIVE.contains(player);
        if (continuing && SpiritProgression.energy(player) <= 0) { exhaust(player, now); return false; }
        String reason = refusal(player, continuing);
        if (!SpiritCombat.available(player) || weaponBusy(player) || reason != null) {
            stop(player);
            if (reason != null && !reason.equals("cooldown") && now >= s.flashNoticeAt) {
                player.displayClientMessage(Component.translatable("message.orimod.flash." + reason), true);
                s.flashNoticeAt = now + 40;
            }
            return false;
        }
        if (!continuing) { ACTIVE.add(player); s.flashActiveSince = now; }
        s.flashUntil = now + LEASE_TICKS;
        SpiritFxLifecycle.sync(player, now);
        return true;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ACTIVE.contains(player)) return;
        var s = player.getData(SpiritData.ENERGY_STATE);
        long now = player.level().getGameTime();
        if (SpiritProgression.energy(player) <= 0) { exhaust(player, now); return; }
        if (!active(player) || weaponBusy(player) || refusal(player, true) != null) { stop(player); return; }
        if (s.flashLastTick == now) return;
        s.flashLastTick = now;
        float energy = SpiritProgression.energy(player);
        SpiritProgression.spendEnergy(player, Math.min(energy, COST_PER_TICK));
        if (SpiritProgression.energy(player) <= 0) {
            exhaust(player, now);
            return;
        }
        long age = now - s.flashActiveSince + 1;
        var level = player.serverLevel();
        for (var target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
                e -> CombatTargets.canHit(player, e) && e.distanceToSqr(player) <= RADIUS * RADIUS)) {
            if (target instanceof GloomSpitter spitter) {
                spitter.dazzle(DAZZLE_TICKS);
                if (age % GLOOM_INTERVAL == 0) target.hurt(player.damageSources().indirectMagic(player, player), GLOOM_DAMAGE);
            } else if (target instanceof GloomHawk hawk) {
                hawk.dazzle(DAZZLE_TICKS, player.position());
                if (age % GLOOM_INTERVAL == 0) target.hurt(player.damageSources().indirectMagic(player, player), GLOOM_DAMAGE);
            } else if (target instanceof GloomHeart || target instanceof GloomMatriarch) {
                if (age % BOSS_INTERVAL == 0) target.hurt(player.damageSources().indirectMagic(player, player), BOSS_DAMAGE);
            } else if (target.getType().is(EntityTypeTags.UNDEAD) && age % UNDEAD_INTERVAL == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0));
                target.hurt(player.damageSources().indirectMagic(player, player), UNDEAD_DAMAGE);
            }
        }
        if (lanternDue(age)) {
            SpiritLanternBlock.igniteAround(level, player.position(), RADIUS, LANTERN_TICKS);
            cn.wwl.orimod.block.PlatformLight.push(level,player.position(),cn.wwl.orimod.block.PlatformRules.Source.FLASH);
        }
        if (age % 20 == 0) cn.wwl.orimod.block.BlightInteractions.area(player, player.position(), RADIUS, cn.wwl.orimod.world.BlightRules.Source.FLASH);
        if (cleanseDue(age)) GloomWaterBlock.cleanse(level, player.blockPosition(), CLEANSE_RADIUS);
    }

    private static void exhaust(ServerPlayer player, long now) {
        player.getData(SpiritData.COOLDOWNS).flashReadyAt = cn.wwl.orimod.progression.SpiritCooldowns.now(player) + COOLDOWN_TICKS;
        stop(player);
    }

    /** Lifecycle code calls this before STOP_ALL; avoids recursive FX synchronization. */
    public static void forget(ServerPlayer player) {
        ACTIVE.remove(player);
        var s = player.getData(SpiritData.ENERGY_STATE);
        s.flashUntil = s.flashActiveSince = Long.MIN_VALUE;
        s.chargeFlameUntil = Long.MIN_VALUE;
    }

    public static void stop(ServerPlayer player) {
        boolean wasActive = ACTIVE.remove(player);
        var s = player.getData(SpiritData.ENERGY_STATE);
        s.flashUntil = s.flashActiveSince = Long.MIN_VALUE;
        if (wasActive) SpiritFxLifecycle.sync(player, player.level().getGameTime());
    }

    public static void opening(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }
    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && DamageInterrupts.action(player, event.getSource(), event.getNewDamage())) stop(player);
    }
    public static void unload(LevelEvent.Unload event) {
        for (var player : Set.copyOf(ACTIVE)) if (player.level() == event.getLevel()) SpiritFxLifecycle.stopAll(player, "unload");
    }
    public static void reset() { for (var player : Set.copyOf(ACTIVE)) forget(player); }

    /** NeoForge 21.1 PositionCheck uses FAIL; only the small active set is examined, never all online players. */
    public static void spawn(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getEntity() instanceof Monster)) return;
        var at = new Vec3(event.getX(), event.getY(), event.getZ());
        for (var player : ACTIVE) {
            if (suppresses(true, event.getSpawnType(), player.serverLevel() == event.getLevel().getLevel(), active(player), player.distanceToSqr(at))) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                return;
            }
        }
    }
}
