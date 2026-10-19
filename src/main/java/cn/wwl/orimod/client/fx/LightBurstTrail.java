package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.fx.OriParticles;
import cn.wwl.orimod.fx.SpiritParticleOptions;
import net.minecraft.world.entity.Entity;

/** R39: the Light Burst has no model; it is drawn as a bright core with a short glowing trail. */
public final class LightBurstTrail {
    public static void tick(Entity burst) {
        var level = burst.level();
        try {
            level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xFFF2C0, .55f, 4), true, burst.getX(), burst.getY(), burst.getZ(), 0, 0, 0);
            level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xFFD27A, .3f, 10), false,
                    burst.getX() + (level.random.nextDouble() - .5) * .2, burst.getY() + (level.random.nextDouble() - .5) * .2,
                    burst.getZ() + (level.random.nextDouble() - .5) * .2, 0, 0, 0);
        } catch (Throwable ignored) { }
    }
    private LightBurstTrail() {}
}
