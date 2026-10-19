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
            level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xF4ECFF, .7f, 3), true, e.getX(), e.getY() + .6, e.getZ(), 0, 0, 0);
            for (int i = 0; i < 2; i++)
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), i == 0 ? 0x8A3AD8 : 0xC060FF, .55f, 8), true,
                        e.getX() + (r.nextDouble() - .5) * .7, e.getY() + .15 + r.nextDouble() * .65, e.getZ() + (r.nextDouble() - .5) * .7, 0, .005, 0);
        } catch (Throwable ignored) { }
    }

    /** R50: boss aura; the telegraphed slam is marked on the ground with a closing ring. */
    public static void heart(cn.wwl.orimod.entity.GloomHeart e) {
        var level = e.level(); var r = level.random;
        int mist = e.phase() >= 2 ? 0xFF4070 : 0xA050FF;
        try {
            for (int i = 0; i < 3; i++)
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), mist, .9f, 12), true,
                        e.getX() + (r.nextDouble() - .5) * 3.2, e.getY() + r.nextDouble() * 2.4, e.getZ() + (r.nextDouble() - .5) * 3.2, 0, .01, 0);
            if (e.attack() != cn.wwl.orimod.entity.GloomHeart.IDLE)
                for (int i = 0; i < 3; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 3.4;
                    level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(), 0xFF80E0, .35f, 8), true,
                            e.getX() + Math.cos(a) * d, e.getY() + .8 + (r.nextDouble() - .5) * 1.5, e.getZ() + Math.sin(a) * d, -Math.cos(a) * .3, 0, -Math.sin(a) * .3);
                }
        } catch (Throwable ignored) { }
    }

    /** R61: hawk wake; red sparks while it telegraphs a dive, a dark trail while diving. */
    public static void hawk(cn.wwl.orimod.entity.GloomHawk e) {
        var level = e.level(); var r = level.random;
        try {
            if (e.state() == cn.wwl.orimod.entity.GloomHawk.TELEGRAPH)
                for (int i = 0; i < 3; i++) level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(), 0xFF3040, .4f, 8), true,
                        e.getX() + (r.nextDouble() - .5) * 2, e.getY() + .4 + (r.nextDouble() - .5), e.getZ() + (r.nextDouble() - .5) * 2, 0, 0, 0);
            else if (e.state() == cn.wwl.orimod.entity.GloomHawk.DIVE)
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0x5A1A6C, .55f, 10), true, e.getX(), e.getY() + .3, e.getZ(), 0, 0, 0);
            else if (r.nextInt(3) == 0)
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0x8A3AB8, .3f, 10), false, e.getX(), e.getY() + .3, e.getZ(), 0, 0, 0);
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
