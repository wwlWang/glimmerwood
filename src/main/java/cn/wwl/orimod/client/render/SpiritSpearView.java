package cn.wwl.orimod.client.render;

import cn.wwl.orimod.combat.SpiritSpear;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Keep the owner's fresh projectile/trail out of their eyes; spectators and third-person see the original bolt. */
public final class SpiritSpearView {
    private SpiritSpearView() { }

    public static float opacity(SpiritSpear spear, Vec3 position, float age) {
        var mc=Minecraft.getInstance();
        boolean own=mc.player!=null && spear.getOwner()==mc.player && mc.getCameraEntity()==mc.player;
        return opacity(own,mc.options.getCameraType().isFirstPerson(),age,
                (float)position.distanceTo(mc.gameRenderer.getMainCamera().getPosition()));
    }

    public static float opacity(boolean own, boolean firstPerson, float age, float eyeDistance) {
        if (!own || !firstPerson) return 1;
        // The bolt extends 1.1 blocks backwards; the old .6-block spawn point put its crossed ribbons behind the eye.
        float t=Math.clamp(Math.min(age-3,eyeDistance-2),0,1);
        return t*t*(3-2*t);
    }
}
