package cn.wwl.orimod.fx;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.network.SpiritFxPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Server owner of persistent spirit effects and of the movement bursts.
 * Persistent effects are derived from authoritative movement state every tick, refreshed with a heartbeat,
 * and explicitly stopped on form switch, death, dimension change and logout, so no client can keep a
 * glide loop or tether after the state that justified it ended.
 */
public final class SpiritFxLifecycle {
    private static final Map<UUID, EnumSet<SpiritFxKind>> ACTIVE = new HashMap<>();
    private static final Map<String, Integer> STOPS = new TreeMap<>();
    private SpiritFxLifecycle() {}

    /** The persistent effects the current movement state justifies. */
    public static EnumSet<SpiritFxKind> desired(ServerPlayer player) {
        var set = EnumSet.noneOf(SpiritFxKind.class);
        if (!player.isAlive() || player.isSpectator() || !SpiritData.isSpirit(player)) return set;
        var s = player.getData(SpiritData.MOVEMENT);
        if (s.mode == MoveMode.GLIDE) set.add(SpiritFxKind.GLIDE);
        if (s.grapple != null) set.add(SpiritFxKind.GRAPPLE_TETHER);
        if (s.bash != null) set.add(SpiritFxKind.BASH_AIM);
        if (cn.wwl.orimod.combat.Flash.active(player)) set.add(SpiritFxKind.FLASH);
        if (cn.wwl.orimod.progression.Regenerate.active(player)) set.add(SpiritFxKind.REGENERATE);
        return set;
    }

    public static Set<SpiritFxKind> active(ServerPlayer player) {
        var set = ACTIVE.get(player.getUUID());
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public static int trackedPlayers() { return ACTIVE.size(); }
    /** Stop counts by reason, for tests and the development bridge. */
    public static Map<String, Integer> stops() { return Collections.unmodifiableMap(STOPS); }

    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, player.level().getGameTime());
    }

    public static void sync(ServerPlayer player, long now) {
        var want = desired(player);
        var have = ACTIVE.get(player.getUUID());
        if (have == null) {
            if (want.isEmpty()) return;
            ACTIVE.put(player.getUUID(), have = EnumSet.noneOf(SpiritFxKind.class));
        }
        for (var kind : SpiritFxKind.values()) {
            if (!kind.persistent()) continue;
            boolean wanted = want.contains(kind), running = have.contains(kind);
            if (wanted && !running) { have.add(kind); send(player, kind, SpiritFxPhase.START); }
            else if (!wanted && running) { have.remove(kind); send(player, kind, SpiritFxPhase.STOP); count("state"); }
            else if (wanted && now % SpiritFxTracker.HEARTBEAT_TICKS == 0) send(player, kind, SpiritFxPhase.START);
        }
        if (have.isEmpty()) ACTIVE.remove(player.getUUID());
    }

    /** Ends every persistent effect of the player immediately. */
    public static void stopAll(ServerPlayer player, String reason) {
        cn.wwl.orimod.combat.Flash.forget(player);
        var have = ACTIVE.remove(player.getUUID());
        // Always tell clients: they may hold an effect whose START raced a reconnect.
        send(player, SpiritFxKind.GLIDE, SpiritFxPhase.STOP_ALL);
        if (have != null && !have.isEmpty()) count(reason);
    }

    /** One-shot movement feedback for an accepted server action. */
    public static void moved(ServerPlayer player, MoveMode mode) {
        var kind = switch (mode) {
            case DOUBLE_JUMP -> SpiritFxKind.DOUBLE_JUMP;
            case WALL_JUMP -> SpiritFxKind.WALL_JUMP;
            case DASH -> SpiritFxKind.DASH;
            case BASH_LAUNCH, LAUNCH -> SpiritFxKind.BASH;
            case GRAPPLE -> SpiritFxKind.GRAPPLE_HOOK;
            default -> null;
        };
        if (kind != null) SpiritFx.emit(player.serverLevel(), kind, player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(), player);
    }

    public static void formChanged(ServerPlayer player) {
        stopAll(player, "form");
        SpiritFx.emit(player.serverLevel(), SpiritFxKind.FORM_SWITCH, player.getX(), player.getY() + .4, player.getZ(), player);
    }

    public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer player) stopAll(player, "death"); }
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) stopAll(player, "dimension"); }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) stopAll(player, "logout"); }
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer player) ACTIVE.remove(player.getUUID()); }
    public static void reset() { ACTIVE.clear(); STOPS.clear(); cn.wwl.orimod.combat.Flash.reset(); }

    private static void count(String reason) { STOPS.merge(reason, 1, Integer::sum); }

    private static void send(ServerPlayer player, SpiritFxKind kind, SpiritFxPhase phase) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new SpiritFxPayload(kind, phase, player.getX(), player.getY(), player.getZ(), player.getId()));
    }
}
