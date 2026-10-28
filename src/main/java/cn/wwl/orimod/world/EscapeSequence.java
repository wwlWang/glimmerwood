package cn.wwl.orimod.world;

import cn.wwl.orimod.fx.OriParticles;
import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.fx.SpiritParticleOptions;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R54 escape sequence (after the escapes of Ori): when the Gloom Heart dies, its corruption bursts out of the
 * altar and spreads as a rising violet front. Players near the kill must get out from under the rock (open sky,
 * at least {@link #ESCAPE_DISTANCE} blocks from the altar) before the time runs out. Inside the front they are
 * hurt and slowed. Escaping plays the victory presentation and grants the reward; no blocks are changed.
 */
public final class EscapeSequence {
    public static final int LIMIT_TICKS = 1200, HURT_INTERVAL = 10, REWARD_LIGHT = 100, FRONT_DELAY_TICKS = 160;
    public static final double START_RADIUS = 3, FRONT_SPEED = .2, ESCAPE_DISTANCE = 12;
    public static final float FRONT_DAMAGE = 2;
    public static final String CLEARED = "boss/gloom_heart";

    public record Session(String dimension, Vec3 origin, long start, ServerBossEvent bar) {}
    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();

    public static double frontRadius(long ticks) { return START_RADIUS + FRONT_SPEED * Math.max(0, ticks - FRONT_DELAY_TICKS); }
    public static boolean escaped(double horizontalDistance, boolean openSky) { return openSky && horizontalDistance >= ESCAPE_DISTANCE; }
    public static boolean caught(double horizontalDistance, long ticks) { return ticks >= FRONT_DELAY_TICKS && horizontalDistance < frontRadius(ticks); }

    public static Session session(ServerPlayer player) { return ACTIVE.get(player.getUUID()); }

    public static void start(ServerPlayer player, Vec3 origin) {
        stop(player);
        cn.wwl.orimod.progression.CompanionLines.offer(player, cn.wwl.orimod.progression.CompanionLines.Line.ESCAPE);
        var bar = new ServerBossEvent(Component.translatable("bossbar.orimod.escape"), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        ACTIVE.put(player.getUUID(), new Session(player.level().dimension().location().toString(), origin, player.level().getGameTime(), bar));
        warning(player, FRONT_DELAY_TICKS / 20);
        player.level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.5f, .7f);
    }

    public static void stop(ServerPlayer player) {
        var s = ACTIVE.remove(player.getUUID());
        if (s != null) s.bar().removeAllPlayers();
    }

    public static void stopAll() { ACTIVE.values().forEach(s -> s.bar().removeAllPlayers()); ACTIVE.clear(); }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var s = ACTIVE.get(player.getUUID());
        if (s == null) return;
        var level = (ServerLevel) player.level();
        if (!player.isAlive() || !level.dimension().location().toString().equals(s.dimension())) { fail(player); return; }
        long t = level.getGameTime() - s.start();
        double dist = Math.hypot(player.getX() - s.origin().x, player.getZ() - s.origin().z);
        s.bar().setProgress(EscapeSupport.remaining(t,LIMIT_TICKS));
        if (escaped(dist, level.canSeeSky(player.blockPosition().above()))) { victory(player); return; }
        if (t >= LIMIT_TICKS) { fail(player); return; }
        if (t > 0 && t < FRONT_DELAY_TICKS && t % 20 == 0) warning(player, (int)((FRONT_DELAY_TICKS - t) / 20));
        if (t == FRONT_DELAY_TICKS) title(player, "title.orimod.escape", "subtitle.orimod.escape", 5, 40, 10);
        double radius = frontRadius(t);
        if (caught(dist, t)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, false));
            if (t % HURT_INTERVAL == 0) player.hurt(level.damageSources().magic(), FRONT_DAMAGE);
        }
        // Visuals: the front as a ring of violet light around the altar at the player's height, debris from above.
        if (t % 2 == 0) {
            int points = 28;
            for (int i = 0; i < points; i++) {
                double a = i * Math.PI * 2 / points + t * .02;
                level.sendParticles(player, new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), i % 2 == 0 ? 0xB040FF : 0xFF3A8A, .9f, 14), true,
                        s.origin().x + Math.cos(a) * radius, player.getY() + .2 + (i % 3) * .6, s.origin().z + Math.sin(a) * radius, 1, 0, .1, 0, .02);
            }
            level.sendParticles(player, new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(), 0x9A8A9A, .3f, 30), true,
                    player.getX() + (level.random.nextDouble() - .5) * 8, player.getY() + 5, player.getZ() + (level.random.nextDouble() - .5) * 8, 2, .5, .2, .5, 0);
        }
        if (t % 30 == 0) {
            level.playSound(null, player.getX(), player.getY() + 4, player.getZ(), SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.2f, .5f);
            SpiritFx.emit(level, SpiritFxKind.STOMP_IMPACT, player.getX() + (level.random.nextDouble() - .5) * 6, player.getY(), player.getZ() + (level.random.nextDouble() - .5) * 6, null);
        }
        if (t % 50 == 0) level.playSound(null, s.origin().x, s.origin().y, s.origin().z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2, .5f);
    }

    /** Victory presentation and reward. @return whether this was the first clear (cells granted). */
    public static boolean victory(ServerPlayer player) {
        var session=ACTIVE.remove(player.getUUID());
        if(session==null)return false;
        session.bar().removeAllPlayers();
        var level = (ServerLevel) player.level();
        title(player, "title.orimod.victory", "subtitle.orimod.victory", 10, 70, 20);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1, 1);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1, 1.4f);
        SpiritFx.emit(level, SpiritFxKind.CHECKPOINT_REACHED, player.getX(), player.getY(), player.getZ(), null);
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            level.sendParticles(player, new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xFFE9A0, .7f, 40), true,
                    player.getX() + Math.cos(a) * 1.5, player.getY() + .2, player.getZ() + Math.sin(a) * 1.5, 1, 0, .25, 0, .05);
        }
        String receipt="escape/gloom_heart/"+session.dimension()+"/"+net.minecraft.core.BlockPos.containing(session.origin()).asLong();
        int light=player.getData(SpiritData.PROGRESS).rewards().containsKey(receipt)?10:REWARD_LIGHT;
        cn.wwl.orimod.progression.SpiritProgression.grantLight(player, light);
        var p = player.getData(SpiritData.PROGRESS);
        boolean first = !p.rewards().containsKey(CLEARED);
        var records = new java.util.HashMap<>(p.rewards());
        records.merge(CLEARED, 1, Integer::sum);
        records.put(receipt,1);
        player.setData(SpiritData.PROGRESS, p.withRewards(records));
        if (first) cn.wwl.orimod.progression.SpiritCells.grantFirstClear(player);
        player.displayClientMessage(Component.translatable(first ? "message.orimod.victory.first" : "message.orimod.victory.again", light), false);
        return first;
    }

    private static void fail(ServerPlayer player) {
        stop(player);
        if (player.isAlive()) title(player, "title.orimod.escape_failed", "subtitle.orimod.escape_failed", 10, 50, 20);
    }

    private static void title(ServerPlayer player, String title, String subtitle, int in, int stay, int out) {
        EscapeSupport.title(player,title,subtitle,in,stay,out);
    }

    private static void warning(ServerPlayer player, int seconds) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 0));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("subtitle.orimod.escape_countdown", seconds)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.orimod.escape_warning")));
        float pitch = seconds > 3 ? .9f : 1.2f + (3 - seconds) * .2f;
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, .7f, pitch);
    }

    private EscapeSequence() {}
}
