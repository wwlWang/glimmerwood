package cn.wwl.orimod.client.render;

import cn.wwl.orimod.combat.SpiritSpear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** R88: the thrown Spirit Spear is a bolt of condensed light along its flight (was only a mote trail). */
public final class SpiritSpearRenderer extends EntityRenderer<SpiritSpear> {
    public SpiritSpearRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public void render(SpiritSpear spear, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        float opacity=SpiritSpearView.opacity(spear,spear.getPosition(partial),spear.tickCount+partial);
        if(opacity<=0)return;
        var v = spear.getDeltaMovement();
        if (v.lengthSqr() < 1e-6) return;
        var dir = new Vector3f((float) v.x, (float) v.y, (float) v.z).normalize();
        var side = Math.abs(dir.y) > .95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0).cross(dir).normalize();
        var pose = poses.last();
        var tip = new Vector3f(dir).mul(.55f).add(0, .15f, 0);
        var tail = new Vector3f(dir).mul(-1.1f).add(0, .15f, 0);
        float pulse = .9f + .1f * (float) Math.sin(SpiritEnergy.time() * 18);
        SpiritEnergy.beam(buffers.getBuffer(SpiritEnergy.streak()), pose, tail, tip, .34f, side, SpiritWeaponRenderer.MID, .35f * pulse * opacity);
        SpiritEnergy.beam(buffers.getBuffer(SpiritEnergy.streak()), pose, tail, tip, .12f, side, SpiritWeaponRenderer.CORE, .95f * opacity);
        SpiritEnergy.orb(buffers.getBuffer(SpiritEnergy.glow()), pose, new Vector3f(dir).mul(.4f).add(0, .15f, 0), .28f, SpiritWeaponRenderer.MID, .5f * pulse * opacity);
        super.render(spear, yaw, partial, poses, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(SpiritSpear spear) { return SpiritEnergy.STREAK; }
}
