package cn.wwl.orimod.progression;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.network.CompanionHintPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** R97: Glimmer's first-encounter guidance; Ori-style exploration adapted to persistent, server-owned rewards. */
public final class CompanionHints {
    public static final String PREFIX = "hint/";
    public static final int SCAN_TICKS = 20, RANGE = 10, GAP_TICKS = 200, RECENT_SPEND_TICKS = 200;
    public static final double LOW_ENERGY_FRACTION = .15;

    /** Declaration order is the priority when several discoveries happen together. */
    public enum Hint {
        UPDRAFT("updraft", 6), BASH_ANCHOR("bash_anchor", 8), SPIRIT_PLATFORM("spirit_platform", 8), GRAPPLE_FLOWER("grapple_flower", 10),
        SPIRIT_SAND("spirit_sand", 6), GLOOM_WATER("gloom_water", 6), KEYSTONE_DOOR("keystone_door", 6),
        MAP_STONE("map_stone", 6), CELL_PEDESTAL("cell_pedestal", 6), BOSS_ALTAR("boss_altar", 10),
        SPIRIT_TREE("spirit_tree", 24), GLOOM_CORE("gloom_core",16),
        CRACKED_WALL("cracked_wall", 8), CRACKED_FLOOR("cracked_floor", 8), GLOOM_BEETLE("gloom_beetle",12), MERCHANT("merchant",12),
        LOW_ENERGY("low_energy", 0), SHARD_SLOT("shard_slot", 0);

        public final String id;
        public final int radius;
        Hint(String id, int radius) { this.id = id; this.radius = radius; }
        public String languageKey() { return "hint.orimod." + id; }

        /** Only the two skill-dependent hints have a locked wording on the wire. */
        public static Hint byWireId(String id) {
            if ("updraft_locked".equals(id)) return UPDRAFT;
            if ("grapple_flower_locked".equals(id)) return GRAPPLE_FLOWER;
            for (var hint : values()) if (hint.id.equals(id)) return hint;
            return null;
        }
    }

    private CompanionHints() {}

    public static boolean ready(long now, long lastHintAt) {
        return lastHintAt == Long.MIN_VALUE || (now >= lastHintAt && now - lastHintAt >= GAP_TICKS);
    }

    /** Pure priority/cooldown rule; seen contains actual sent ids, including independent locked variants. */
    public static Hint pick(Set<String> seen, EnumSet<Hint> conditions, int abilities, long now, long lastHintAt) {
        if (!ready(now, lastHintAt)) return null;
        for (var hint : Hint.values()) if (conditions.contains(hint) && !seen.contains(wireId(hint, abilities))) return hint;
        return null;
    }

    /** Re-evaluate the currently applicable wording after skills change, even if all prior wording was seen. */
    private static EnumSet<Hint> unseen(Set<String> seen, int abilities) {
        var hints = EnumSet.allOf(Hint.class);
        hints.removeIf(hint -> seen.contains(wireId(hint, abilities)));
        return hints;
    }

    public static boolean lowEnergy(float energy, float maximum, long now, long lastSpent) {
        return maximum > 0 && energy >= 0 && energy < maximum * LOW_ENERGY_FRACTION
                && lastSpent != Long.MIN_VALUE && now >= lastSpent && now - lastSpent <= RECENT_SPEND_TICKS;
    }

    public static Set<String> seen(SpiritProgress progress) {
        return progress.rewards().keySet().stream().filter(k -> k.startsWith(PREFIX))
                .map(k -> k.substring(PREFIX.length())).collect(Collectors.toUnmodifiableSet());
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().getGameTime() % SCAN_TICKS == 0) check(player);
    }

    /** Also used by server integration tests; never sends more than one payload in an eligible scan. */
    public static void check(ServerPlayer player) {
        if (!SpiritData.isSpirit(player) || !player.isAlive() || player.isSpectator()) return;
        var session = player.getData(SpiritData.ENERGY_STATE);
        long now = player.level().getGameTime();
        if (!ready(now, session.lastCompanionHintAt)) return;
        var progress = player.getData(SpiritData.PROGRESS);
        var seen = seen(progress);
        int abilities = player.getData(SpiritData.ABILITIES);
        var unseen = unseen(seen, abilities);
        if (unseen.isEmpty()) return;
        var positions = scan(player, unseen);
        if(unseen.contains(Hint.GLOOM_BEETLE))player.level().getEntitiesOfClass(cn.wwl.orimod.entity.GloomBeetle.class,player.getBoundingBox().inflate(12),
                beetle->beetle.isAlive() && player.distanceToSqr(beetle)<=144 && player.hasLineOfSight(beetle)).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).ifPresent(beetle->positions.put(Hint.GLOOM_BEETLE,beetle.blockPosition()));
        if (unseen.contains(Hint.MERCHANT)) player.level().getEntitiesOfClass(cn.wwl.orimod.entity.GladesMerchant.class, player.getBoundingBox().inflate(12),
                merchant -> merchant.isAlive() && player.distanceToSqr(merchant) <= 144 && player.hasLineOfSight(merchant)).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).ifPresent(merchant -> positions.put(Hint.MERCHANT, merchant.blockPosition()));
        var conditions = EnumSet.noneOf(Hint.class);
        conditions.addAll(positions.keySet());
        if (lowEnergy(SpiritProgression.energy(player), SpiritProgression.maxEnergy(player), now, session.lastHintSpendAt))
            conditions.add(Hint.LOW_ENERGY);
        if (progress.ownedShards() != 0 && progress.equippedShards() == 0) conditions.add(Hint.SHARD_SLOT);
        var hint = pick(seen, conditions, abilities, now, session.lastCompanionHintAt);
        if (hint == null) return;
        String id = wireId(hint, abilities);
        var rewards = new HashMap<>(progress.rewards());
        rewards.put(PREFIX + id, 1);
        player.setData(SpiritData.PROGRESS, progress.withRewards(rewards));
        session.lastCompanionHintAt = now;
        PacketDistributor.sendToPlayer(player, new CompanionHintPayload(id,
                Optional.ofNullable(positions.get(hint))));
    }

    public static String wireId(Hint hint, int abilities) {
        boolean locked = hint == Hint.UPDRAFT && (abilities & Ability.GLIDE.bit) == 0
                || hint == Hint.GRAPPLE_FLOWER && (abilities & Ability.GRAPPLE.bit) == 0;
        return hint.id + (locked ? "_locked" : "");
    }

    /** Loaded chunks only; palette prefilter skips sections with no unseen hint blocks before visiting their cells. */
    public static EnumMap<Hint, BlockPos> scan(ServerPlayer player, Set<String> seen) {
        return scan(player, unseen(seen, player.getData(SpiritData.ABILITIES)));
    }

    private static EnumMap<Hint, BlockPos> scan(ServerPlayer player, EnumSet<Hint> unseen) {
        var found = new EnumMap<Hint, BlockPos>(Hint.class);
        if (unseen.isEmpty()) return found;
        var level = player.serverLevel();
        var centre = player.blockPosition();
        // Only an unseen tree hint expands the loaded-section scan; ordinary hints keep their original radii.
        int range = unseen.contains(Hint.SPIRIT_TREE) ? Hint.SPIRIT_TREE.radius : unseen.contains(Hint.GLOOM_CORE) ? 16 : RANGE;
        int minX = centre.getX() - range, maxX = centre.getX() + range;
        int minZ = centre.getZ() - range, maxZ = centre.getZ() + range;
        int minY = Math.max(level.getMinBuildHeight(), centre.getY() - range);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + range);
        var pos = new BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            var chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                if (!section.maybeHas(state -> unseen.contains(blockHint(state)))) continue;
                for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++)
                    for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++)
                        for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
                            var hint = blockHint(section.getBlockState(x & 15, y & 15, z & 15));
                            if (!unseen.contains(hint)) continue;
                            pos.set(x, y, z);
                            double distance = pos.distToCenterSqr(player.position());
                            var previous = found.get(hint);
                            if (distance <= hint.radius * hint.radius && (previous == null || distance < previous.distToCenterSqr(player.position())))
                                found.put(hint, pos.immutable());
                        }
            }
        }
        return found;
    }

    private static Hint blockHint(BlockState state) {
        if (state.is(OriBlocks.GLOOM_CORE)) return Hint.GLOOM_CORE;
        if (state.is(OriBlocks.UPDRAFT.get())) return Hint.UPDRAFT;
        if (state.is(OriBlocks.BASH_ANCHOR.get())) return Hint.BASH_ANCHOR;
        if (state.is(OriBlocks.SPIRIT_PLATFORM)) return Hint.SPIRIT_PLATFORM;
        if (state.is(OriBlocks.GRAPPLE_FLOWER.get())) return Hint.GRAPPLE_FLOWER;
        if (state.is(OriBlocks.SPIRIT_SAND.get())) return Hint.SPIRIT_SAND;
        if (state.is(OriBlocks.GLOOM_WATER.get())) return Hint.GLOOM_WATER;
        if (state.is(OriBlocks.KEYSTONE_DOOR.get())) return Hint.KEYSTONE_DOOR;
        if (state.is(OriBlocks.MAP_STONE.get())) return Hint.MAP_STONE;
        if (state.is(OriBlocks.LIFE_CELL_PEDESTAL.get()) || state.is(OriBlocks.ENERGY_CELL_PEDESTAL.get())) return Hint.CELL_PEDESTAL;
        if (state.is(OriBlocks.GLOOM_ALTAR.get()) || state.is(OriBlocks.STORM_ALTAR.get())) return Hint.BOSS_ALTAR;
        if (state.is(OriBlocks.SPIRIT_TREE_HEART.get())) return Hint.SPIRIT_TREE;
        if (state.is(OriBlocks.CRACKED_RUIN_WALL.get())) return Hint.CRACKED_WALL;
        if (state.is(OriBlocks.CRACKED_RUIN_FLOOR.get())) return Hint.CRACKED_FLOOR;
        return null;
    }

    public static void core(ServerPlayer player,BlockPos pos) {
        if (!SpiritData.isSpirit(player) || !SpiritData.isAwakened(player) || !player.isAlive() || player.isSpectator()) return;
        var progress=player.getData(SpiritData.PROGRESS);var session=player.getData(SpiritData.ENERGY_STATE);long now=player.level().getGameTime();
        if (progress.rewards().containsKey(PREFIX+Hint.GLOOM_CORE.id) || !ready(now,session.lastCompanionHintAt)) return;
        var rewards=new HashMap<>(progress.rewards());rewards.put(PREFIX+Hint.GLOOM_CORE.id,1);player.setData(SpiritData.PROGRESS,progress.withRewards(rewards));
        session.lastCompanionHintAt=now;PacketDistributor.sendToPlayer(player,new CompanionHintPayload(Hint.GLOOM_CORE.id,Optional.of(pos)));
    }

    public static boolean reset(ServerPlayer player) {
        var progress = player.getData(SpiritData.PROGRESS);
        var rewards = new HashMap<>(progress.rewards());
        rewards.keySet().removeIf(k -> k.startsWith(PREFIX));
        player.setData(SpiritData.PROGRESS, progress.withRewards(rewards));
        player.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt = Long.MIN_VALUE;
        return true;
    }
}
