package cn.wwl.orimod.world;

import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * R41 Spirit Wells (Ori): every ruin shrine is a well. Using one restores health and energy (once a minute),
 * and from a well the player can choose any well discovered in the same dimension.
 * Discovery reuses the shrine "discover/<dimension>/<x,y,z>" progress keys.
 */
public final class SpiritWell {
    public static final int REFILL_COOLDOWN_TICKS = 1200;
    public static final double USE_RANGE = 6;

    /** Pure: the discovered well after {@code current} (sorted, wrapping) in the same dimension, or null. */
    public static String next(List<String> discoverKeys, String current) {
        String dimension = current.substring(0, current.lastIndexOf('/') + 1);
        var wells = discoverKeys.stream().filter(k -> k.startsWith("discover/" + dimension)).map(k -> k.substring("discover/".length()))
                .filter(k -> k.indexOf('/', dimension.length()) < 0).sorted().toList();
        if (wells.isEmpty() || wells.size() == 1 && wells.get(0).equals(current)) return null;
        for (String well : wells) if (well.compareTo(current) > 0) return well;
        return wells.get(0).equals(current) ? (wells.size() > 1 ? wells.get(1) : null) : wells.get(0);
    }

    /** Pure: shrine position from a well key "<dimension>/x,y,z", or null when malformed. */
    public static BlockPos position(String key) {
        try {
            String[] xyz = key.substring(key.lastIndexOf('/') + 1).split(",");
            return xyz.length == 3 ? new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])) : null;
        } catch (RuntimeException e) { return null; }
    }

    /** Called when the player uses a shrine: refill health and energy if the well is ready. */
    public static boolean refill(ServerPlayer player) {
        if (!SpiritData.isAwakened(player)) return false;
        var state = player.getData(SpiritData.ENERGY_STATE);
        long now = player.level().getGameTime();
        if (now < state.wellReadyAt) return false;
        if (player.getHealth() >= player.getMaxHealth() && SpiritProgression.energy(player) >= SpiritProgression.maxEnergy(player)) return false;
        state.wellReadyAt = now + REFILL_COOLDOWN_TICKS;
        player.setHealth(player.getMaxHealth());
        player.setData(SpiritData.ENERGY, SpiritProgression.maxEnergy(player));
        SpiritFx.emit(player.serverLevel(), SpiritFxKind.CHECKPOINT_REACHED, player.position(), player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .6f, 1.5f);
        player.displayClientMessage(Component.translatable("message.orimod.well.refilled"), true);
        return true;
    }

    public static final int TRAVEL_COOLDOWN_TICKS = 40;

    /** Pure list used by the screen and server. No scans, no chunk loads, no cross-dimension targets. */
    public static List<BlockPos> discovered(java.util.Collection<String> keys, String dimension) {
        String prefix = "discover/" + dimension + "/";
        return keys.stream().filter(k -> k.startsWith(prefix) && k.indexOf('/', prefix.length()) < 0)
                .map(SpiritWell::position).filter(java.util.Objects::nonNull).distinct()
                .sorted(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ)).toList();
    }
    private static boolean canTravel(ServerPlayer p, BlockPos from) {
        return from != null && SpiritData.isAwakened(p) && p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping()
                && p.distanceToSqr(Vec3.atCenterOf(from)) <= USE_RANGE * USE_RANGE
                && p.serverLevel().getChunkSource().getChunkNow(from.getX() >> 4, from.getZ() >> 4) != null
                && p.level().getBlockState(from).getBlock() instanceof SpiritWellBlock
                && p.getData(SpiritData.PROGRESS).rewards().containsKey("discover/" + ChallengeSession.key(p.level().dimension(), from))
                && SpiritChallenge.session(p) == null && !cn.wwl.orimod.progression.Restoration.active(p);
    }
    public static boolean open(ServerPlayer p, BlockPos from) {
        if (!canTravel(p, from)) return false;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new cn.wwl.orimod.network.WellOfferPayload(from, p.level().dimension().location().toString()));
        return true;
    }
    /** Legacy cycle API; player interactions use the explicit selection overload below. */
    public static boolean travel(ServerPlayer p, BlockPos from) {
        if (!canTravel(p, from)) return false;
        String current = ChallengeSession.key(p.level().dimension(), from);
        String target = current;
        for (int i = 0; i < count(p.getData(SpiritData.PROGRESS).rewards().keySet(),p.level().dimension().location().toString()); i++) {
            target = next(List.copyOf(p.getData(SpiritData.PROGRESS).rewards().keySet()), target);
            if (target == null || target.equals(current)) return false;
            if (travel(p, from, position(target), p.level().dimension().location().toString())) return true;
        }
        return false;
    }
    public static boolean travel(ServerPlayer p, BlockPos from, BlockPos to, String dimension) {
        if (!p.level().dimension().location().toString().equals(dimension) || !canTravel(p, from)
                || to == null || from.equals(to) || p.level().getGameTime() < p.getData(SpiritData.ENERGY_STATE).wellTravelReadyAt
                || !discovered(p.getData(SpiritData.PROGRESS).rewards().keySet(),dimension).contains(to)) return false;
        var level = p.serverLevel();
        BlockPos stand = safeDestination(level,to,p);
        if (stand == null) {
            p.displayClientMessage(Component.translatable("message.orimod.well.unsafe"),true); return false;
        }
        p.getData(SpiritData.ENERGY_STATE).wellTravelReadyAt = level.getGameTime() + TRAVEL_COOLDOWN_TICKS;
        SpiritFx.emit(level,SpiritFxKind.FORM_SWITCH,p.position(),p);
        Vec3 at=Vec3.atBottomCenterOf(stand);
        p.teleportTo(level,at.x,at.y,at.z,p.getYRot(),p.getXRot());p.fallDistance=0;
        SpiritFx.emit(level,SpiritFxKind.FORM_SWITCH,at,p);
        level.playSound(null,stand,SoundEvents.CHORUS_FRUIT_TELEPORT,SoundSource.PLAYERS,.7f,1.4f);
        p.displayClientMessage(Component.translatable("message.orimod.well.travelled",stand.getX(),stand.getY(),stand.getZ()),true);
        return true;
    }
    /** Only a selected, already discovered well may load its destination chunk. All fallback positions are checked. */
    public static BlockPos safeDestination(ServerLevel level, BlockPos shrine, ServerPlayer player) {
        if (!level.getWorldBorder().isWithinBounds(shrine) || level.isOutsideBuildHeight(shrine)) return null;
        level.getChunk(shrine);
        var state=level.getBlockState(shrine);
        if (!(state.getBlock() instanceof SpiritWellBlock)) return null;
        var candidates=new java.util.ArrayList<BlockPos>();
        if (cn.wwl.orimod.block.SanctuaryAssembly.assembled(state)) {
            // The 3x3 basin owns its rim: arrival must be outside that footprint.
            var front=state.getValue(SpiritWellBlock.FACING);
            for(int radius=2;radius<=3;radius++) {
                candidates.add(shrine.relative(front,radius));
                for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    candidates.add(shrine.relative(d,radius));
                    for(int side=1;side<radius;side++) {
                        candidates.add(shrine.relative(d,radius).relative(d.getClockWise(),side));
                        candidates.add(shrine.relative(d,radius).relative(d.getCounterClockWise(),side));
                    }
                }
            }
        } else {
            // Preserve existing saves, structure shrines and course checkpoints exactly.
            candidates.add(shrine.relative(state.getValue(SpiritWellBlock.FACING)));
            candidates.add(shrine.above());
            for (var d: net.minecraft.core.Direction.Plane.HORIZONTAL) candidates.add(shrine.relative(d));
        }
        for(var stand:candidates) {
            if(!level.getWorldBorder().isWithinBounds(stand) || !cn.wwl.orimod.progression.SoulLink.usable(level,stand)) continue;
            var at=Vec3.atBottomCenterOf(stand);
            var box=player==null ? new net.minecraft.world.phys.AABB(at.x-.3,at.y,at.z-.3,at.x+.3,at.y+1.8,at.z+.3)
                    : player.getBoundingBox().move(at.subtract(player.position()));
            if(level.noCollision(player,box) && !level.containsAnyLiquid(box)) return stand;
        }
        return null;
    }
    public static BlockPos destination(ServerLevel level,BlockPos from,java.util.Collection<String> keys) {
        String current=ChallengeSession.key(level.dimension(),from), target=current;
        for(int i=0;i<keys.size();i++) {
            target=next(List.copyOf(keys),target);
            if(target==null || target.equals(current))return null;
            var pos=position(target);if(pos==null)continue;
            var stand=safeDestination(level,pos,null);if(stand!=null)return stand;
        }
        return null;
    }

    /** Wells discovered in a dimension (client panel count). */
    public static int count(java.util.Collection<String> keys, String dimension) {
        return (int) keys.stream().filter(k -> k.startsWith("discover/" + dimension + "/")).count();
    }

    private SpiritWell() {}
}
