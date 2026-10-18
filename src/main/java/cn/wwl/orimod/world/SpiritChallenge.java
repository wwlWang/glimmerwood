package cn.wwl.orimod.world;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.network.RuinOfferPayload;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpiritChallenge {
    public static final int LIMIT = 90 * 20;
    private static final Map<UUID, ChallengeSession> SESSIONS = new HashMap<>();
    private SpiritChallenge() {}
    public static ChallengeSession session(ServerPlayer player) { return SESSIONS.get(player.getUUID()); }
    public static int sessionCount() { return SESSIONS.size(); }

    public static void stopAll(net.minecraft.server.MinecraftServer server) {
        for (var entry : Map.copyOf(SESSIONS).entrySet()) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) finish(player, "exit", true);
            else discardTarget(server, entry.getValue());
        }
        SESSIONS.clear();
    }
    public static void discardAll() { SESSIONS.clear(); }

    public static void offer(ServerPlayer player, BlockPos shrine, RuinKind kind) {
        if (!player.isAlive() || player.distanceToSqr(Vec3.atCenterOf(shrine)) > 36) return;
        String key = ChallengeSession.key(player.level().dimension(), shrine);
        if (SpiritData.isAwakened(player) && SpiritProgression.reward(player, "discover/" + key, 15, 0))
            player.displayClientMessage(Component.translatable("challenge.orimod.discovered", kind.title(), 15), false);
        var progress = player.getData(SpiritData.PROGRESS);
        PacketDistributor.sendToPlayer(player, new RuinOfferPayload(shrine, kind.id, requirement(player, shrine, kind),
                progress.rewards().getOrDefault("clear/" + key, 0), progress.rewards().getOrDefault("best/" + key, 0), session(player) != null));
    }

    /** Empty means the server has checked the live shrine, load state, route and player prerequisites. */
    private static String requirement(ServerPlayer player, BlockPos shrine, RuinKind kind) {
        if (!SpiritData.isAwakened(player)) return "awakening";
        if (!SpiritData.isSpirit(player)) return "form";
        if ((player.getData(SpiritData.ABILITIES) & kind.abilities) != kind.abilities) return "abilities";
        if (kind == RuinKind.CAVE && player.getInventory().items.stream().noneMatch(stack -> stack.is(OriItems.SPIRIT_BLADE))) return "blade";
        var placement = RuinPlacement.atShrine(kind, shrine, player.level().getBlockState(shrine));
        if (!routeValid(player.serverLevel(), player, kind, placement)) return "damaged";
        return "";
    }

    private static boolean routeValid(ServerLevel level, ServerPlayer player, RuinKind kind, RuinPlacement placement) {
        for (var anchor : kind.layout().critical()) {
            var pos = placement.block(anchor.position());
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(BuiltInRegistries.BLOCK.get(anchor.block()))) return false;
        }
        for (var clearance : kind.layout().clearances()) {
            var from = placement.block(clearance.from()); var to = placement.block(clearance.to());
            if (!level.hasChunkAt(from) || !level.hasChunkAt(to)
                    || !level.hasChunkAt(new BlockPos(from.getX(), from.getY(), to.getZ()))
                    || !level.hasChunkAt(new BlockPos(to.getX(), from.getY(), from.getZ()))) return false;
            var bounds = placement.clearance(clearance).deflate(.001);
            for (var shape : level.getBlockCollisions(player, bounds)) if (!shape.isEmpty()) return false;
        }
        if (!safe(level, player, placement.point(kind.layout().safe()))) return false;
        for (var point : kind.layout().checkpoints())
            if (!safe(level, player, placement.point(point.position()))) return false;
        return true;
    }

    private static boolean safe(ServerLevel level, ServerPlayer player, Vec3 point) {
        var feet = BlockPos.containing(point);
        if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)
                || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        for (var pos : new BlockPos[]{feet.below(), feet, feet.above()}) {
            var block = level.getBlockState(pos);
            if (block.is(net.minecraft.tags.BlockTags.FIRE) || block.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || block.is(net.minecraft.world.level.block.Blocks.CACTUS) || block.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                    || block.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) || block.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return false;
        }
        var bounds = new AABB(point.x - .3, point.y + .001, point.z - .3, point.x + .3, point.y + 1.8, point.z + .3);
        return level.noCollision(player, bounds) && !level.noCollision(player, bounds.move(0, -.1, 0));
    }

    public static void request(ServerPlayer player, BlockPos shrine, boolean start) {
        if (!start) { finish(player, "exit", true); return; }
        if (session(player) != null || !player.isAlive() || player.isPassenger() || player.isSpectator()
                || player.distanceToSqr(Vec3.atCenterOf(shrine)) > 36 || !player.serverLevel().hasChunkAt(shrine)) return;
        if (!(player.level().getBlockState(shrine).getBlock() instanceof RuinShrineBlock block)) return;
        String problem = requirement(player, shrine, block.kind);
        if (!problem.isEmpty()) { offer(player, shrine, block.kind); return; }
        var placement = RuinPlacement.atShrine(block.kind, shrine, player.level().getBlockState(shrine));
        var s = new ChallengeSession(block.kind, shrine, placement, player.level().dimension(), player.level().getGameTime());
        s.baseline = actionCount(player, s.kind.layout().checkpoints().getFirst().action());
        player.setData(SpiritData.CHALLENGE_RECOVERY, new ChallengeRecovery(s.dimension.location().toString(),
                BlockPos.containing(s.placement.point(s.kind.layout().safe()))));
        SESSIONS.put(player.getUUID(), s);
        player.setData(SpiritData.CHALLENGE_ACTIVE, true);
        returnSafely(player, player.serverLevel(), s.placement.point(s.kind.layout().safe()));
        player.displayClientMessage(Component.translatable("challenge.orimod.started", s.kind.title(), Component.keybind("key.orimod.panel")), false);
    }

    private static int actionCount(ServerPlayer player, String action) {
        var movement = player.getData(SpiritData.MOVEMENT);
        return switch (action) {
            case "wall_jump" -> movement.acceptedWallJumps;
            case "jump" -> movement.acceptedJumps;
            case "dash" -> movement.acceptedDashes;
            case "bash" -> movement.acceptedBashes;
            case "glide" -> movement.startedGlides;
            case "grapple" -> movement.completedGrapples;
            case "stomp" -> movement.stompImpacts;
            default -> 0;
        };
    }

    public static boolean ownsTarget(ServerPlayer player, PracticeTarget target) {
        var s = session(player);
        return s != null && s.id.equals(target.session) && target.getUUID().equals(s.target) && s.dimension.equals(player.level().dimension());
    }
    public static void targetDefeated(ServerPlayer player, PracticeTarget target) {
        if (ownsTarget(player, target)) complete(player, session(player));
    }
    private static void spawnTarget(ServerPlayer player, ChallengeSession s) {
        var point = s.placement.point(s.kind.layout().checkpoints().get(s.checkpoint).position());
        var target = OriEntities.PRACTICE_TARGET.get().create(player.serverLevel());
        if (target == null) { finish(player, "target", true); return; }
        target.owner = player.getUUID(); target.session = s.id;
        target.moveTo(point.x, point.y, point.z, 180, 0);
        s.target = target.getUUID();
        if (!player.serverLevel().addFreshEntity(target)) finish(player, "target", true);
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var s = session(player);
        if (s == null) { recover(player); return; }
        if (!player.isAlive()) { finish(player, "death", false); return; }
        if (!s.dimension.equals(player.level().dimension())) { finish(player, "dimension", false); return; }
        if (!SpiritData.isSpirit(player) || player.isSpectator() || player.isPassenger()) { finish(player, "form", true); return; }
        long elapsed = player.level().getGameTime() - s.started;
        if (elapsed >= LIMIT) { finish(player, "timeout", true); return; }
        Vec3 relative = s.placement.local(player.position());
        var size = s.kind.layout().size();
        if (relative.x < -2 || relative.z < -2 || relative.x > size.getX() + 2 || relative.z > size.getZ() + 2
                || relative.y < 1 || player.isInWaterOrBubble()) { finish(player, "fall", true); return; }
        if (elapsed % 20 == 0 && !routeValid(player.serverLevel(), player, s.kind, s.placement)) { finish(player, "damaged", true); return; }
        var checkpoint = s.kind.layout().checkpoints().get(s.checkpoint);
        Vec3 destination = s.placement.point(checkpoint.position());
        if (!checkpoint.action().equals("blade") && player.onGround() && player.position().distanceToSqr(destination) <= 3.24
                && Math.abs(player.getY() - destination.y) < .35 && actionCount(player, checkpoint.action()) - s.baseline >= checkpoint.count()) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, destination.x, destination.y + .5, destination.z, 12, .4, .5, .4, .015);
            s.checkpoint++;
            if (s.checkpoint >= s.kind.layout().checkpoints().size()) { complete(player, s); return; }
            var next = s.kind.layout().checkpoints().get(s.checkpoint);
            s.baseline = actionCount(player, next.action());
            if (next.action().equals("blade")) spawnTarget(player, s);
        }
        if (elapsed % 10 == 0) {
            var next = s.kind.layout().checkpoints().get(s.checkpoint);
            var point = s.placement.point(next.position());
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, point.x, point.y + .2, point.z, 1, .2, .1, .2, 0);
            player.displayClientMessage(Component.translatable("challenge.orimod.status", s.checkpoint + 1, s.kind.layout().checkpoints().size(),
                    Component.translatable("challenge.orimod.action." + next.action()), (LIMIT - elapsed + 19) / 20), true);
        }
        if (s.target != null) {
            var target = player.serverLevel().getEntity(s.target);
            var targetPoint = s.placement.point(s.kind.layout().checkpoints().get(s.checkpoint).position());
            if (!(target instanceof PracticeTarget crystal) || !s.id.equals(crystal.session) || target.position().distanceToSqr(targetPoint) > 4)
                finish(player, "target", true);
        }
    }

    private static void complete(ServerPlayer player, ChallengeSession s) {
        int ticks = (int)(player.level().getGameTime() - s.started);
        boolean first = SpiritProgression.reward(player, "clear/" + s.key(), 30, 3);
        var progress = player.getData(SpiritData.PROGRESS);
        var records = new HashMap<>(progress.rewards());
        int best = records.getOrDefault("best/" + s.key(), Integer.MAX_VALUE);
        records.put("best/" + s.key(), Math.min(best, Math.max(1, ticks)));
        player.setData(SpiritData.PROGRESS, progress.withRewards(records));
        cleanup(player, s);
        player.displayClientMessage(Component.translatable("challenge.orimod.complete", s.kind.title(), String.format(java.util.Locale.ROOT, "%.2f", ticks / 20.0), first ? 30 : 3), false);
    }

    private static void cleanup(ServerPlayer player, ChallengeSession s) {
        SESSIONS.remove(player.getUUID());
        player.setData(SpiritData.CHALLENGE_ACTIVE, false);
        player.setData(SpiritData.CHALLENGE_RECOVERY, ChallengeRecovery.EMPTY);
        discardTarget(player.server, s);
    }

    private static void discardTarget(net.minecraft.server.MinecraftServer server, ChallengeSession s) {
        var level = server.getLevel(s.dimension);
        if (level != null && s.target != null && level.getEntity(s.target) instanceof PracticeTarget target && s.id.equals(target.session)) target.discard();
    }

    public static void finish(ServerPlayer player, String reason, boolean teleport) {
        var s = session(player);
        if (s == null) return;
        cleanup(player, s);
        var level = player.server.getLevel(s.dimension);
        if (teleport && player.isAlive() && level != null && player.level() == level)
            returnSafely(player, level, s.placement.point(s.kind.layout().safe()));
        if (player.isAlive() && !reason.equals("death")) player.displayClientMessage(Component.translatable("challenge.orimod.ended." + reason), false);
    }

    private static void recover(ServerPlayer player) {
        var recovery = player.getData(SpiritData.CHALLENGE_RECOVERY);
        if (recovery.dimension().isEmpty()) return;
        player.setData(SpiritData.CHALLENGE_ACTIVE, false);
        player.setData(SpiritData.CHALLENGE_RECOVERY, ChallengeRecovery.EMPTY);
        if (!player.isAlive() || !recovery.dimension().equals(player.level().dimension().location().toString())) return;
        returnSafely(player, player.serverLevel(), Vec3.atBottomCenterOf(recovery.safe()));
        player.displayClientMessage(Component.translatable("challenge.orimod.ended.reload"), false);
    }

    private static void returnSafely(ServerPlayer player, ServerLevel level, Vec3 preferred) {
        SpiritCombat.cancel(player, "challenge");
        SpiritMovement.cancel(player, player.getData(SpiritData.MOVEMENT));
        // Search only already-loaded nearby columns. Never change terrain to manufacture a safe point.
        Vec3 destination = safe(level, player, preferred) ? preferred : null;
        for (int radius = 1; destination == null && radius <= 5; radius++) {
            for (int dx = -radius; destination == null && dx <= radius; dx++) for (int dz = -radius; destination == null && dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                    Vec3 candidate = preferred.add(dx, dy, dz);
                    if (safe(level, player, candidate)) { destination = candidate; break; }
                }
            }
        }
        if (destination == null) { player.displayClientMessage(Component.translatable("challenge.orimod.no_safe_return"), false); return; }
        player.teleportTo(level, destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) finish(player, "exit", true); }
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) finish(player, "dimension", false); }
    public static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finish(player, "death", false);
    }
}
