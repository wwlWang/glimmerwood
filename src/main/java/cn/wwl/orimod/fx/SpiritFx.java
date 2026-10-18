package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.SpiritFxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server entry point for spirit effects. Same 32-block audience as vanilla {@code sendParticles}. */
public final class SpiritFx {
    public static final double RANGE = 32;
    private SpiritFx() {}

    public static void emit(ServerLevel level, SpiritFxKind kind, Vec3 at, Entity source) {
        emit(level, kind, at.x, at.y, at.z, source);
    }

    public static void emit(ServerLevel level, SpiritFxKind kind, double x, double y, double z, Entity source) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, RANGE,
                new SpiritFxPayload(kind, x, y, z, source == null ? SpiritFxPayload.NO_SOURCE : source.getId()));
    }
}
