package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.fx.FxBudget;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.network.SpiritFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Spawns spirit effect bursts under the player's quality and particle settings. */
public final class SpiritFxClient {
    private static final RandomSource RANDOM = RandomSource.create();
    private SpiritFxClient() {}

    static ParticleOptions particle(SpiritFxKind kind) {
        return kind == SpiritFxKind.BLADE_HIT ? ParticleTypes.CRIT : ParticleTypes.END_ROD;
    }

    public static void play(SpiritFxPayload payload) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        var visuals = OriClientConfig.visuals();
        var kind = payload.kind();
        var split = FxBudget.of(kind, visuals.quality(), visuals.advancedParticles());
        var options = particle(kind);
        for (int i = 0; i < split.total(); i++) {
            // Same distribution as ServerLevel.sendParticles: gaussian offset and speed.
            double x = payload.x() + RANDOM.nextGaussian() * kind.spreadX();
            double y = payload.y() + RANDOM.nextGaussian() * kind.spreadY();
            double z = payload.z() + RANDOM.nextGaussian() * kind.spreadZ();
            double vx = RANDOM.nextGaussian() * kind.speed(), vy = RANDOM.nextGaussian() * kind.speed(), vz = RANDOM.nextGaussian() * kind.speed();
            boolean essential = i < split.essential();
            try { level.addParticle(options, essential, x, y, z, vx, vy, vz); }
            catch (Throwable ignored) { return; }
        }
        if (kind == SpiritFxKind.STOMP_IMPACT && mc.player != null && payload.sourceEntity() == mc.player.getId()) {
            SpiritScreenFeedback.stomp(visuals);
        }
    }
}
