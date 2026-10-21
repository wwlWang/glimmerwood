package cn.wwl.orimod.progression;

import cn.wwl.orimod.network.CompanionHintPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.RuinKind;
import cn.wwl.orimod.world.RuinStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** R102: one short-lived pending line per player. Hints win the shared 200-tick speaking interval. */
public final class CompanionLines {
    public static final String PREFIX = "line/", BATTLE_STARTED = "orimod:companion_battle_started";
    public enum Line {
        FIRST_RUIN_FOREST_SPRING("first_ruin_forest_spring", true, 0),
        FIRST_RUIN_CREEK_RUINS("first_ruin_creek_ruins", true, 0),
        FIRST_RUIN_CAVE_ALTAR("first_ruin_cave_altar", true, 0),
        BOSS_START("boss_start", false, 6000), BOSS_DEFEATED("boss_defeated", false, 0),
        ESCAPE("escape", false, 0), TIDE_BEGIN("tide_begin", false, 0), TIDE_END("tide_end", false, 0),
        TREE_FOUND("tree_found", true, 0), LOW_HEALTH("low_health", false, 1200, 60), NIGHT_ONE("night_one", true, 0);
        public final String id;
        public final boolean once;
        public final int cooldown;
        public final int maxWaitTicks;
        // Allow the shared 200-tick hint gap plus a 20-tick scan and some margin.
        Line(String id, boolean once, int cooldown) { this(id, once, cooldown, 240); }
        Line(String id, boolean once, int cooldown, int maxWaitTicks) {
            this.id = id; this.once = once; this.cooldown = cooldown; this.maxWaitTicks = maxWaitTicks;
        }
        public String languageKey() { return "line.orimod." + id; }
        public static Line byId(String id) { for (var l : values()) if (l.id.equals(id)) return l; return null; }
        public static Line ruin(RuinKind kind) {
            return switch (kind) { case FOREST -> FIRST_RUIN_FOREST_SPRING; case CREEK -> FIRST_RUIN_CREEK_RUINS; case CAVE -> FIRST_RUIN_CAVE_ALTAR; };
        }
    }
    public record Pending(Line line, long at) {}
    /** Unsaved ENERGY_STATE member. Losing a pending line never consumes its once-only record. */
    public static final class Session {
        public Pending pending;
        public final EnumMap<Line, Long> spoken = new EnumMap<>(Line.class);
        public float previousHealth = Float.NaN;
        public boolean wasNight;
        public RuinKind insideRuin;
        public void clearPending() { pending = null; previousHealth = Float.NaN; wasNight = false; insideRuin = null; }
    }
    public static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && SpiritData.isAwakened(p) && SpiritData.isSpirit(p);
    }
    public static boolean available(Line line, boolean seen, long now, long last) {
        return !(line.once && seen) && (last == Long.MIN_VALUE || now >= last && now - last >= line.cooldown);
    }
    public static boolean expired(Pending pending, long now) { return now < pending.at || now - pending.at > pending.line.maxWaitTicks; }
    public static boolean healthEdge(float previous, float current) {
        return Float.isFinite(previous) && Float.isFinite(current) && previous >= .25f && current >= 0 && current < .25f;
    }
    public static boolean nightCondition(boolean overworld, boolean surface, long dayTime) {
        return overworld && surface && Math.floorMod(dayTime, 24000) >= 13000;
    }
    public static boolean allRuinsSeen(SpiritProgress progress) {
        for (var kind : RuinKind.values()) if (!progress.rewards().containsKey(PREFIX + Line.ruin(kind).id)) return false;
        return true;
    }
    public static boolean offer(ServerPlayer p, Line line) {
        if (!eligible(p)) return false;
        var session = p.getData(SpiritData.ENERGY_STATE).lines;
        long now = p.level().getGameTime();
        if (!available(line, p.getData(SpiritData.PROGRESS).rewards().containsKey(PREFIX + line.id), now,
                session.spoken.getOrDefault(line, Long.MIN_VALUE))) return false;
        session.pending = new Pending(line, now);
        return true;
    }
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        observe(p);
    }
    /** Observe transitions, not repeatedly the low-health/night condition. Unsent lines can trigger on a future entry. */
    public static void observe(ServerPlayer p) {
        var session = p.getData(SpiritData.ENERGY_STATE).lines;
        if (!eligible(p)) { session.clearPending(); return; }
        var level = p.serverLevel(); long now = level.getGameTime();
        if (session.pending != null && expired(session.pending, now)) session.pending = null;
        if (now % CompanionHints.SCAN_TICKS == 0 && !allRuinsSeen(p.getData(SpiritData.PROGRESS))) {
            var kind = ruinAt(level, p.blockPosition());
            if (kind != null && kind != session.insideRuin) offer(p, Line.ruin(kind));
            session.insideRuin = kind;
        }
        boolean night = nightCondition(level.dimension() == Level.OVERWORLD,
                surface(p), level.getDayTime());
        if (night && !session.wasNight) offer(p, Line.NIGHT_ONE);
        session.wasNight = night;
        float health = p.getHealth() / p.getMaxHealth();
        if (healthEdge(session.previousHealth, health)) offer(p, Line.LOW_HEALTH);
        session.previousHealth = health;
    }
    /** Sky light updates asynchronously. The synchronous heightmap also rejects a newly placed roof. */
    public static boolean surface(ServerPlayer p) {
        var pos = p.blockPosition(); var level = p.serverLevel();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && pos.getY() > chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                pos.getX() & 15, pos.getZ() & 15) && level.canSeeSky(pos);
    }
    /** Only the player's current chunk and loaded starts referenced by it. Never use a loading StructureManager lookup. */
    public static RuinKind ruinAt(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        for (var start : chunk.getAllStarts().values()) {
            var kind = containing(start, pos); if (kind != null) return kind;
        }
        for (var entry : chunk.getAllReferences().entrySet()) {
            if (!(entry.getKey() instanceof RuinStructure)) continue;
            for (long reference : entry.getValue()) {
                var at = new ChunkPos(reference);
                var origin = level.getChunkSource().getChunkNow(at.x, at.z);
                if (origin == null) continue;
                var kind = containing(origin.getStartForStructure(entry.getKey()), pos);
                if (kind != null) return kind;
            }
        }
        return null;
    }
    private static RuinKind containing(StructureStart start, BlockPos pos) {
        if (start == null || !start.isValid() || !(start.getStructure() instanceof RuinStructure ruin) || !start.getBoundingBox().isInside(pos)) return null;
        return start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().isInside(pos)) ? ruin.kind : null;
    }
    /** After player hints AND the level's tide transitions. A queued line never bypasses this scan cadence. */
    public static void flushLevel(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % CompanionHints.SCAN_TICKS == 0)
            for (var player : level.players()) flush(player);
    }
    public static boolean flush(ServerPlayer p) {
        var state = p.getData(SpiritData.ENERGY_STATE); var session = state.lines;
        var pending = session.pending;
        if (pending == null) return false;
        if (!eligible(p) || expired(pending, p.level().getGameTime())) { session.pending = null; return false; }
        long now = p.level().getGameTime(); var line = pending.line;
        if (!CompanionHints.ready(now, state.lastCompanionHintAt)) return false;
        var progress = p.getData(SpiritData.PROGRESS);
        if (!available(line, progress.rewards().containsKey(PREFIX + line.id), now, session.spoken.getOrDefault(line, Long.MIN_VALUE))) {
            session.pending = null; return false;
        }
        PacketDistributor.sendToPlayer(p, CompanionHintPayload.line(line));
        session.pending = null; session.spoken.put(line, now); state.lastCompanionHintAt = now;
        if (line.once) {
            var records = new HashMap<>(progress.rewards()); records.put(PREFIX + line.id, 1);
            p.setData(SpiritData.PROGRESS, progress.withRewards(records));
        }
        return true;
    }
    /** Each boss instance is one encounter; persisted on the boss so unload/reload cannot restart its opening. */
    public static void bossStarted(Mob boss, double radius) {
        if (!(boss.level() instanceof ServerLevel level) || !(boss.getTarget() instanceof ServerPlayer)
                || boss.getPersistentData().getBoolean(BATTLE_STARTED)) return;
        boss.getPersistentData().putBoolean(BATTLE_STARTED, true);
        for (var p : level.players()) if (p.distanceToSqr(boss) <= radius * radius) offer(p, Line.BOSS_START);
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) p.getData(SpiritData.ENERGY_STATE).lines.clearPending(); }
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) p.getData(SpiritData.ENERGY_STATE).lines.clearPending(); }
    private CompanionLines() {}
}
