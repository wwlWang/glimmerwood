package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.combat.Flash;
import cn.wwl.orimod.config.OriServerConfig;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.network.GloomTidePayload;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** R101: Overworld-only night lifecycle. No chunk tickets, persistent player records or forced mob loot. */
public final class GloomTide {
    public static final String TAG = "orimod:tide";
    public static final float INTENSITY = .35f;
    private static final Map<ServerLevel, Night> NIGHTS = new IdentityHashMap<>();
    private static final class Night {
        long forcedDay = Long.MIN_VALUE, stoppedDay = Long.MIN_VALUE, activeDay = Long.MIN_VALUE;
        boolean active;
        final GloomTideRules.WaveClaims claims = new GloomTideRules.WaveClaims();
    }
    private static Night state(ServerLevel level) { return NIGHTS.computeIfAbsent(level, l -> new Night()); }
    public static boolean tagged(Entity entity) { return entity.getPersistentData().getBoolean(TAG); }
    public static boolean isActive(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return false;
        var s = state(level); long time = level.getDayTime(), day = GloomTideRules.day(time);
        boolean spawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        return s.stoppedDay != day && (s.forcedDay == day
                ? GloomTideRules.allowed(time, level.getDifficulty(), spawning, OriServerConfig.enabled())
                : GloomTideRules.isTideNight(time, level.getMoonPhase(), level.getDifficulty(), spawning, OriServerConfig.enabled()));
    }
    /** Only moon phase is bypassed. Null means success; otherwise a translated command failure key. */
    public static String start(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return "commands.orimod.tide.overworld";
        if (!GloomTideRules.night(level.getDayTime())) return "commands.orimod.tide.night";
        if (!GloomTideRules.allowed(level.getDayTime(), level.getDifficulty(),
                level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING), OriServerConfig.enabled()))
            return "commands.orimod.tide.disabled";
        var s = state(level); s.forcedDay = GloomTideRules.day(level.getDayTime()); s.stoppedDay = Long.MIN_VALUE;
        tickLevel(level);
        return null;
    }
    public static void stop(ServerLevel level) {
        var s = state(level); s.forcedDay = Long.MIN_VALUE; s.stoppedDay = GloomTideRules.day(level.getDayTime());
        refresh(level);
    }
    private static void refresh(ServerLevel level) {
        var s = state(level); long day = GloomTideRules.day(level.getDayTime());
        if (!GloomTideRules.night(level.getDayTime()) || s.forcedDay != day) s.forcedDay = Long.MIN_VALUE;
        if (!GloomTideRules.night(level.getDayTime()) || s.stoppedDay != day) s.stoppedDay = Long.MIN_VALUE;
        boolean next = isActive(level);
        if (s.active && (!next || s.activeDay != day)) {
            s.active = false;
            // getAllEntities is the loaded entity manager, not a scan/load of saved chunks.
            var expired = new ArrayList<Entity>();
            for (var entity : level.getAllEntities()) if (tagged(entity)) expired.add(entity);
            for (var entity : expired) {
                level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + .5, entity.getZ(), 5, .3, .4, .3, .02);
                entity.discard();
            }
            for (var p : level.getServer().getPlayerList().getPlayers()) {
                var ledger = p.getData(SpiritData.ENERGY_STATE).tide;
                if (ledger.participating) {
                    int reward = SpiritProgression.grantLight(p, ledger.finish());
                    cn.wwl.orimod.progression.CompanionLines.offer(p, cn.wwl.orimod.progression.CompanionLines.Line.TIDE_END);
                    p.displayClientMessage(Component.translatable("message.orimod.tide.end", ledger.kills, reward), true);
                }
            }
            for (var p : level.players()) sync(p);
        }
        if (next && !s.active) {
            s.active = true; s.activeDay = day;
            for (var p : level.players()) sync(p);
        }
    }
    public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) tickLevel(level);
    }
    public static void tickLevel(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        refresh(level);
        if (!isActive(level)) return;
        long now = level.getGameTime(), day = GloomTideRules.day(level.getDayTime());
        for (var p : level.players()) {
            var ledger = p.getData(SpiritData.ENERGY_STATE).tide;
            if (!ledger.participating || ledger.day != day) {
                if (!eligible(p)) continue;
                ledger.begin(day, now);
                cn.wwl.orimod.progression.CompanionLines.offer(p, cn.wwl.orimod.progression.CompanionLines.Line.TIDE_BEGIN);
                p.displayClientMessage(Component.translatable("message.orimod.tide.begin"), true);
                p.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, .6f, .65f);
            }
            if (now >= ledger.nextWaveAt) {
                ledger.nextWaveAt = now + OriServerConfig.interval();
                if (eligible(p)) spawnWave(p, level.random, GloomTideDefense.QUERY);
            }
        }
    }
    public static boolean eligible(ServerPlayer p) {
        var level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD || !p.isAlive() || p.isSpectator() || p.isCreative()
                || !SpiritData.isAwakened(p) || !SpiritData.isSpirit(p)
                || !(level.canSeeSky(p.blockPosition()) || p.getY() >= level.getSeaLevel() - 5)
                || SpiritChallenge.session(p) != null || EscapeSequence.session(p) != null) return false;
        // Boss arenas are small; all nearby fighters are protected, not just the boss's current target.
        return level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), mob -> mob.isAlive()
                && (mob instanceof GloomHeart || mob instanceof GloomMatriarch)
                && mob.getTarget() != null && p.distanceToSqr(mob) <= 48 * 48).isEmpty();
    }
    public static int nearby(ServerPlayer p) {
        return p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(GloomTideRules.NEARBY_RADIUS),
                mob -> mob.isAlive() && tagged(mob) && GloomTideRules.within(p.distanceToSqr(mob), GloomTideRules.NEARBY_RADIUS)).size();
    }
    /** A query seam permits deterministic terrain/defense tests while retaining the production wave path. */
    public static int spawnWave(ServerPlayer p, RandomSource random, GloomTideDefense.Query defense) {
        var level = p.serverLevel();
        if (!isActive(level) || !eligible(p) || Flash.active(p)) return 0;
        int spitters = 1 + random.nextInt(3), total = spitters + (random.nextFloat() < .3f ? 1 : 0), spawned = 0;
        for (int i = 0; i < total; i++) {
            if (!GloomTideRules.roomForMob(nearby(p), OriServerConfig.maxNearby())) break;
            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = GloomTideRules.MIN_SPAWN + random.nextDouble() * (GloomTideRules.MAX_SPAWN - GloomTideRules.MIN_SPAWN);
                int x = (int) Math.floor(p.getX() + Math.cos(angle) * radius), z = (int) Math.floor(p.getZ() + Math.sin(angle) * radius);
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                var pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                var point = Vec3.atBottomCenterOf(pos);
                double dx = point.x - p.getX(), dz = point.z - p.getZ();
                long chunk = ChunkPos.asLong(x >> 4, z >> 4), now = level.getGameTime();
                if (!GloomTideRules.spawnDistance(dx * dx + dz * dz) || !validGround(level, pos)
                        || !state(level).claims.available(now, chunk, p.getUUID()) || defense.protects(level, point)) continue;
                Mob mob = i < spitters ? OriMobs.GLOOM_SPITTER.get().create(level) : OriMobs.GLOOM_HAWK.get().create(level);
                if (mob == null) continue;
                mob.moveTo(point.x, point.y, point.z, random.nextFloat() * 360, 0);
                mob.getPersistentData().putBoolean(TAG, true);
                if (!level.noCollision(mob)) { mob.discard(); continue; }
                // EVENT deliberately ignores natural darkness checks, but honors other mods' finalize/cancel hooks.
                EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
                if (!level.addFreshEntity(mob)) { mob.discard(); continue; }
                state(level).claims.claim(now, chunk, p.getUUID());
                mob.setTarget(p); spawned++; break;
            }
        }
        return spawned;
    }
    public static boolean validGround(ServerLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet.below()) || level.isOutsideBuildHeight(feet.above())
                || !level.getWorldBorder().isWithinBounds(feet) || level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4) == null) return false;
        var floor = level.getBlockState(feet.below());
        if (floor.is(OriBlocks.SPIRIT_LOG) || floor.is(OriBlocks.SPIRIT_LEAVES) || floor.is(BlockTags.LEAVES)
                || !floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, feet.below(), Direction.UP)) return false;
        for (var pos : List.of(feet, feet.above())) {
            var block = level.getBlockState(pos);
            if (!block.getFluidState().isEmpty() || !block.getCollisionShape(level, pos).isEmpty()) return false;
        }
        return true;
    }
    public static void death(LivingDeathEvent event) {
        if (!event.isCanceled() && tagged(event.getEntity()) && event.getSource().getEntity() instanceof ServerPlayer p
                && isActive(p.serverLevel()) && SpiritData.isAwakened(p)) {
            var ledger = p.getData(SpiritData.ENERGY_STATE).tide;
            if (!ledger.participating || ledger.day != GloomTideRules.day(p.level().getDayTime())) ledger.begin(GloomTideRules.day(p.level().getDayTime()), p.level().getGameTime());
            ledger.kills++;
        }
    }
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && tagged(event.getEntity()) && !isActive(level)) {
            event.setCanceled(true); event.getEntity().discard();
        }
    }
    public static void sync(ServerPlayer p) { boolean active = isActive(p.serverLevel()); PacketDistributor.sendToPlayer(p, new GloomTidePayload(active, active ? INTENSITY : 0)); }
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) { var ledger = p.getData(SpiritData.ENERGY_STATE).tide; ledger.participating = false; ledger.kills = 0; }
    }
    public static Map<String, Object> status(ServerPlayer p) {
        var ledger = p.getData(SpiritData.ENERGY_STATE).tide;
        boolean active = isActive(p.serverLevel());
        return Map.of("active", active, "nearby", nearby(p), "kills", ledger.day == GloomTideRules.day(p.level().getDayTime()) ? ledger.kills : 0,
                "nextWaveIn", active && ledger.participating ? Math.max(0, ledger.nextWaveAt - p.level().getGameTime()) : 0L);
    }
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) { NIGHTS.remove(level); GloomTideDefense.clear(level); }
    }
    public static void reset() { NIGHTS.clear(); GloomTideDefense.reset(); }
    private GloomTide() {}
}
