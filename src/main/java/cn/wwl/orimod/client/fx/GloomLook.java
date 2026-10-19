package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.fx.OriParticles;
import cn.wwl.orimod.fx.SpiritParticleOptions;
import net.minecraft.world.entity.Entity;

/** R42: particle "models" for the Gloom Spitter and its shots (no model or texture assets). */
public final class GloomLook {
    public static void body(Entity e) {
        var level = e.level(); var r = level.random;
        try {
            if (e instanceof cn.wwl.orimod.entity.GloomSpitter spitter && spitter.charge() > 0)   // telegraph: motes converge
                for (int i = 0; i < 2; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 1.4;
                    level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(), 0xD070FF, .3f, 7), true,
                            e.getX() + Math.cos(a) * d, e.getY() + .5 + (r.nextDouble() - .5), e.getZ() + Math.sin(a) * d, -Math.cos(a) * .18, 0, -Math.sin(a) * .18);
                }
            level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xE8D8FF, .45f, 3), true, e.getX(), e.getY() + .5, e.getZ(), 0, 0, 0);
            for (int i = 0; i < 2; i++)
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), i == 0 ? 0x5A2A8C : 0x8A3AB8, .38f, 8), true,
                        e.getX() + (r.nextDouble() - .5) * .7, e.getY() + .15 + r.nextDouble() * .65, e.getZ() + (r.nextDouble() - .5) * .7, 0, .005, 0);
        } catch (Throwable ignored) { }
    }

    public static void shot(Entity e) {
        try {
            e.level().addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xB060FF, .4f, 4), true, e.getX(), e.getY(), e.getZ(), 0, 0, 0);
            e.level().addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(), 0x6A2A9C, .25f, 8), false, e.getX(), e.getY(), e.getZ(), 0, 0, 0);
        } catch (Throwable ignored) { }
    }
    private GloomLook() {}
}
