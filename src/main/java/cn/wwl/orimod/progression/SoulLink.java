package cn.wwl.orimod.progression;

import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * R34 Soul Link (Ori and the Blind Forest): spend spirit energy to bind a respawn point where you stand.
 * One link at a time; a new link replaces the old one. On respawn the player returns to the link if it is
 * still safe, ahead of beds and world spawn (it is the newer, deliberate choice). Survives death and relog.
 */
public final class SoulLink {
    public static final float COST = 25;
    public static final int COOLDOWN_TICKS = 100;

    public record Link(ResourceKey<Level> dimension, BlockPos pos) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Link::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Link::pos)).apply(i, Link::new));
    }

    /** Pure rule: why a link cannot be made now, or empty when it can. */
    public static Optional<String> refusal(boolean spirit, boolean onGround, boolean inChallenge, boolean inFluid, float energy, long now, long readyAt) {
        if (!spirit) return Optional.of("not_spirit");
        if (inChallenge) return Optional.of("challenge");
        if (!onGround || inFluid) return Optional.of("unstable");
        if (now < readyAt) return Optional.of("cooldown");
        if (energy < COST) return Optional.of("energy");
        return Optional.empty();
    }

    /** Pure rule: a stored link is usable when the feet and head blocks are free and the floor holds the player. */
    public static boolean safe(boolean feetFree, boolean headFree, boolean floorSolid) { return feetFree && headFree && floorSolid; }

    public static boolean create(ServerPlayer player) {
        long now = player.level().getGameTime();
        var state = player.getData(SpiritData.ENERGY_STATE);
        var refusal = refusal(SpiritData.isSpirit(player), player.onGround(), player.getData(SpiritData.CHALLENGE_ACTIVE),
                player.isInLiquid(), SpiritProgression.energy(player), now, state.soulLinkReadyAt);
        if (refusal.isPresent()) {
            player.displayClientMessage(Component.translatable("message.orimod.soul_link." + refusal.get()), true);
            return false;
        }
        if (!SpiritProgression.spendEnergy(player, COST)) return false;
        state.soulLinkReadyAt = now + COOLDOWN_TICKS;
        var link = new Link(player.level().dimension(), player.blockPosition());
        player.setData(SpiritData.SOUL_LINK, Optional.of(link));
        var level = player.serverLevel();
        SpiritFx.emit(level, SpiritFxKind.CHECKPOINT_REACHED, player.position().add(0, .1, 0), player);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, .7f, 1.4f);
        player.displayClientMessage(Component.translatable("message.orimod.soul_link.created"), true);
        return true;
    }

    /** Respawn hook: move to the link when it is safe; otherwise keep vanilla respawn and forget the broken link. */
    public static void respawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var link = player.getData(SpiritData.SOUL_LINK);
        if (link.isEmpty()) return;
        ServerLevel level = player.server.getLevel(link.get().dimension());
        BlockPos pos = link.get().pos();
        if (level == null || !usable(level, pos)) {
            player.setData(SpiritData.SOUL_LINK, Optional.empty());
            player.displayClientMessage(Component.translatable("message.orimod.soul_link.broken"), false);
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(pos);
        player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("message.orimod.soul_link.returned"), true);
    }

    public static boolean usable(ServerLevel level, BlockPos pos) {
        level.getChunk(pos); // load synchronously; respawn is rare
        return safe(level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty(),
                level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty(),
                level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP));
    }

    private SoulLink() {}
}
