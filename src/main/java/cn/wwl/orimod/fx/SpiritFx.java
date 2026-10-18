package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.SpiritFxPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server entry point for spirit effects. Bursts use the same 32-block audience as vanilla
 * {@code sendParticles} and play their formal sound cue once; persistent effects go through
 * {@link SpiritFxLifecycle}.
 */
public final class SpiritFx {
    public static final double RANGE = 32;
    private SpiritFx() {}

    public static void emit(ServerLevel level, SpiritFxKind kind, Vec3 at, Entity source) {
        emit(level, kind, at.x, at.y, at.z, source);
    }

    public static void emit(ServerLevel level, SpiritFxKind kind, double x, double y, double z, Entity source) {
        emit(level, kind, x, y, z, source, 1f);
    }

    /** @param pitchScale multiplies the cue pitch, e.g. for rising blade combo stages. */
    public static void emit(ServerLevel level, SpiritFxKind kind, double x, double y, double z, Entity source, float pitchScale) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, RANGE,
                new SpiritFxPayload(kind, x, y, z, source == null ? SpiritFxPayload.NO_SOURCE : source.getId()));
        var cue = SpiritSoundCue.of(kind);
        if (cue != null && !cue.loop()) {
            var sound = BuiltInRegistries.SOUND_EVENT.getHolder(ResourceLocation.parse(cue.sound()));
            sound.ifPresent(holder -> level.playSeededSound(null, x, y, z, holder, SoundSource.PLAYERS, cue.volume(), cue.pitch() * pitchScale, level.random.nextLong()));
        }
    }
}
