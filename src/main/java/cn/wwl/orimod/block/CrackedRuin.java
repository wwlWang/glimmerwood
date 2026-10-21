package cn.wwl.orimod.block;

import cn.wwl.orimod.combat.SpiritSpear;
import cn.wwl.orimod.item.SpiritHammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** R103: ability adapters and a bounded, immutable collapse plan. No ability's combat rules live here. */
public final class CrackedRuin {
    public enum Kind { WALL, FLOOR }
    public enum Source { CHARGE_FLAME, SMASH, LIGHT_BURST, BASHED_IMPACT, SPEAR, STOMP }
    public static final int CHAIN_RADIUS = 8, CHAIN_LIMIT = 64;
    public record Step(BlockPos pos, int delay) {}
    public static boolean breaks(Kind kind, Source source) {
        return kind != null && source != null && (kind == Kind.FLOOR ? source == Source.STOMP : source != Source.STOMP);
    }
    public static Kind kind(BlockState state) { return state.getBlock() instanceof CrackedRuinBlock cracked ? cracked.kind : null; }

    /** Face connected, within a Euclidean radius; delay is ceil(distance), not traversal order. */
    public static List<Step> plan(BlockPos origin, Kind kind, Function<BlockPos, Kind> blocks, int limit) {
        if (kind == null || blocks.apply(origin) != kind || limit <= 0) return List.of();
        var queue = new ArrayDeque<BlockPos>(); var visited = new HashSet<BlockPos>(); var result = new ArrayList<Step>();
        queue.add(origin.immutable()); visited.add(origin.immutable());
        while (!queue.isEmpty() && result.size() < Math.min(limit, CHAIN_LIMIT)) {
            var pos = queue.removeFirst();
            if (pos.distSqr(origin) > CHAIN_RADIUS * CHAIN_RADIUS || blocks.apply(pos) != kind) continue;
            result.add(new Step(pos, (int)Math.ceil(Math.sqrt(pos.distSqr(origin)))));
            for (var direction : Direction.values()) { var next = pos.relative(direction); if (visited.add(next)) queue.addLast(next); }
        }
        return List.copyOf(result);
    }
    public static int hit(ServerLevel level, BlockPos pos, Source source) { return hit(level, pos, source, CHAIN_LIMIT); }
    private static int hit(ServerLevel level, BlockPos pos, Source source, int limit) {
        if (!level.hasChunkAt(pos)) return 0;
        var state = level.getBlockState(pos); var kind = kind(state);
        if (!breaks(kind, source) || state.getValue(CrackedRuinBlock.COLLAPSING)) return 0;
        var plan = plan(pos, kind, p -> {
            if (!level.hasChunkAt(p)) return null;
            var s = level.getBlockState(p);
            return s.getBlock() instanceof CrackedRuinBlock && !s.getValue(CrackedRuinBlock.COLLAPSING) ? kind(s) : null;
        }, limit);
        // Snapshot every member before breaking anything; later placements never join this wave.
        for (var step : plan) {
            var s = level.getBlockState(step.pos);
            var ticks = (net.minecraft.world.ticks.LevelChunkTicks<Block>) level.getChunkAt(step.pos).getBlockTicks();
            ticks.removeIf(t -> t.type() == s.getBlock() && t.pos().equals(step.pos));
            level.setBlock(step.pos, s.setValue(CrackedRuinBlock.COLLAPSING, true), Block.UPDATE_ALL);
            if (step.delay > 0) level.scheduleTick(step.pos, s.getBlock(), step.delay);
        }
        if (!plan.isEmpty()) remove(level, pos, level.getBlockState(pos));
        return plan.size();
    }
    static void remove(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).equals(state)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, 20, .4, .4, .4, .05);
        level.playSound(null, pos, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, .8f, 1);
    }

    /** Loaded section palettes first, shared by ability impacts and the companion's container targeting. */
    public static List<BlockPos> scan(ServerLevel level, AABB box, Predicate<BlockState> target) {
        var found = new ArrayList<BlockPos>();
        int minX = (int)Math.floor(box.minX), maxX = (int)Math.floor(box.maxX), minZ = (int)Math.floor(box.minZ), maxZ = (int)Math.floor(box.maxZ);
        int minY = Math.max(level.getMinBuildHeight(), (int)Math.floor(box.minY)), maxY = Math.min(level.getMaxBuildHeight()-1, (int)Math.floor(box.maxY));
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz); if (chunk == null) continue;
            for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                var section = chunk.getSection(chunk.getSectionIndex(sy << 4)); if (!section.maybeHas(target)) continue;
                for (int x = Math.max(minX,cx<<4); x <= Math.min(maxX,(cx<<4)+15); x++)
                    for (int y = Math.max(minY,sy<<4); y <= Math.min(maxY,(sy<<4)+15); y++)
                        for (int z = Math.max(minZ,cz<<4); z <= Math.min(maxZ,(cz<<4)+15); z++)
                            if (target.test(section.getBlockState(x&15,y&15,z&15))) found.add(new BlockPos(x,y,z));
            }
        }
        return found;
    }
    public static int area(ServerLevel level, ServerPlayer player, Vec3 center, double radius, Source source) {
        if (player == null) return 0;
        return impact(level, player, new AABB(center, center).inflate(radius), p -> p.distToCenterSqr(center) <= radius*radius, source);
    }
    private static int impact(ServerLevel level, ServerPlayer player, AABB box, Predicate<BlockPos> within, Source source) {
        int broken = 0;
        var targets = scan(level, box, s -> s.getBlock() instanceof CrackedRuinBlock || s.is(OriBlocks.SPIRIT_LIGHT_VESSEL));
        for (var pos : targets) if (within.test(pos)) {
            if (level.getBlockState(pos).is(OriBlocks.SPIRIT_LIGHT_VESSEL)) SpiritLightVesselBlock.burst(player, pos);
            else if (broken < CHAIN_LIMIT) broken += hit(level, pos, source, CHAIN_LIMIT-broken);
        }
        return broken;
    }
    public static void smash(ServerPlayer player) {
        var at = SpiritHammerItem.impactPoint(player.position(), player.getYRot());
        impact(player.serverLevel(), player, new AABB(at,at).inflate(SpiritHammerItem.RADIUS,1.5,SpiritHammerItem.RADIUS).expandTowards(0,1,0), p -> true, Source.SMASH);
    }
    public static boolean stompFloor(Vec3 feet, BlockPos pos) {
        return pos.getY() == (int)Math.floor(feet.y-.05) && pos.distToCenterSqr(feet) <= 1.5*1.5;
    }
    public static void stomp(ServerPlayer player) {
        var at = player.position();
        impact(player.serverLevel(), player, new AABB(at,at).inflate(1.5), p -> stompFloor(at,p), Source.STOMP);
        for (var pos : scan(player.serverLevel(),new AABB(at,at).inflate(3),s -> s.is(OriBlocks.SPIRIT_LIGHT_VESSEL)))
            if (pos.distToCenterSqr(at) <= 9) SpiritLightVesselBlock.burst(player,pos);
    }
    public static void spear(SpiritSpear spear, BlockHitResult hit) {
        if (spear.level() instanceof ServerLevel level && spear.getOwner() instanceof ServerPlayer player) {
            hit(level,hit.getBlockPos(),Source.SPEAR); SpiritLightVesselBlock.burst(player,hit.getBlockPos());
        }
    }
    private CrackedRuin() {}
}
