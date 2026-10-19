package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.fx.OriParticles;
import cn.wwl.orimod.fx.SpiritParticleOptions;
import net.minecraft.world.entity.Entity;

/** R60: the Spirit Spear is drawn as a line of bright motes along its flight (no model asset). */
public final class SpearTrail {
    public static void tick(Entity spear) {
        var level = spear.level(); var v = spear.getDeltaMovement();
        try {
            for (int i = 0; i < 5; i++) {
                double t = i / 5.0;
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), i == 0 ? 0xFFFFFF : 0x9FE8FF, i == 0 ? .5f : .32f, 5), true,
                        spear.getX() - v.x * t, spear.getY() - v.y * t, spear.getZ() - v.z * t, 0, 0, 0);
            }
        } catch (Throwable ignored) { }
    }
    private SpearTrail() {}
}
