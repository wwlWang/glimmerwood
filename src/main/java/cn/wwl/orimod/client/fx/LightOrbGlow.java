package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.fx.OriParticles;
import cn.wwl.orimod.fx.SpiritParticleOptions;
import cn.wwl.orimod.item.OriItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** R43: Spirit Light orbs glow with a soft pulsing halo and leave a light trail while they are pulled in. */
public final class LightOrbGlow {
    public static final int CORE = 0xCFF0FF, TRAIL = 0x7FD4FF;

    /** Trail motes per tick for an orb moving at this speed (blocks per tick): none at rest, at most 3. */
    public static int trailCount(double speed) { return speed < .04 ? 0 : (int) Math.min(3, Math.ceil(speed / .15)); }

    /** Halo size for a pulse phase; the halo breathes between .35 and .55. */
    public static float haloSize(long age) { return (float) (.45 + .1 * Math.sin(age * .2)); }

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !item.level().isClientSide || !item.getItem().is(OriItems.SPIRIT_LIGHT)) return;
        var level = item.level(); var r = level.random;
        double y = item.getY() + .25 + Math.sin((item.getAge() + item.bobOffs * 20) / 10.0) * .1 + .1;
        try {
            if (item.tickCount % 2 == 0)
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), CORE, haloSize(item.tickCount), 3), true, item.getX(), y, item.getZ(), 0, 0, 0);
            var v = item.getDeltaMovement(); int n = trailCount(v.length());
            for (int i = 0; i < n; i++) {
                double t = (i + r.nextDouble()) / Math.max(1, n);
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(), TRAIL, .22f, 10), false,
                        item.getX() - v.x * t, y - v.y * t, item.getZ() - v.z * t, 0, .004, 0);
            }
            if (n == 0 && r.nextInt(12) == 0)
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(), TRAIL, .16f, 16), false,
                        item.getX() + (r.nextDouble() - .5) * .4, y, item.getZ() + (r.nextDouble() - .5) * .4, 0, .012, 0);
        } catch (Throwable ignored) { }
    }
    private LightOrbGlow() {}
}
