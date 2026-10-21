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
 * and from a well the player can travel to the next well they have discovered in the same dimension.
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

    /** Travel from the shrine at {@code from} to the next discovered well. */
    public static boolean travel(ServerPlayer player, BlockPos from) {
        var level = player.serverLevel();
        if (from == null || !(level.getBlockState(from).getBlock() instanceof SpiritWellBlock)
                || player.distanceToSqr(Vec3.atCenterOf(from)) > USE_RANGE * USE_RANGE || SpiritChallenge.session(player) != null) return false;
        BlockPos stand = destination(level, from, player.getData(SpiritData.PROGRESS).rewards().keySet());
        if (stand != null) {
            SpiritFx.emit(level, SpiritFxKind.FORM_SWITCH, player.position(), player);
            Vec3 at = Vec3.atBottomCenterOf(stand);
            player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
            player.fallDistance = 0;
            SpiritFx.emit(level, SpiritFxKind.FORM_SWITCH, at, player);
            level.playSound(null, stand, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, .7f, 1.4f);
            player.displayClientMessage(Component.translatable("message.orimod.well.travelled", stand.getX(), stand.getY(), stand.getZ()), true);
            return true;
        }
        player.displayClientMessage(Component.translatable("message.orimod.well.none"), true);
        return false;
    }

    /** Where travel from the well at {@code from} lands: in front of the next intact discovered well, or null. */
    public static BlockPos destination(ServerLevel level, BlockPos from, java.util.Collection<String> rewardKeys) {
        String current = ChallengeSession.key(level.dimension(), from);
        var keys = List.copyOf(rewardKeys);
        String target = current;
        for (int tries = 0; tries < 64; tries++) {
            target = next(keys, target);
            if (target == null || target.equals(current)) return null;
            BlockPos shrine = position(target);
            if (shrine == null) continue;
            level.getChunk(shrine);
            var state = level.getBlockState(shrine);
            if (!(state.getBlock() instanceof SpiritWellBlock)) continue;
            BlockPos stand = shrine.relative(state.getValue(SpiritWellBlock.FACING));
            return cn.wwl.orimod.progression.SoulLink.usable(level, stand) ? stand : shrine.above();
        }
        return null;
    }

    /** Wells discovered in a dimension (client panel count). */
    public static int count(java.util.Collection<String> keys, String dimension) {
        return (int) keys.stream().filter(k -> k.startsWith("discover/" + dimension + "/")).count();
    }

    private SpiritWell() {}
}
