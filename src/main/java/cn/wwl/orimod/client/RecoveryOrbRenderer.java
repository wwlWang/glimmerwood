package cn.wwl.orimod.client;

import cn.wwl.orimod.client.render.SpiritEnergy;
import cn.wwl.orimod.entity.RecoveryOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public final class RecoveryOrbRenderer extends EntityRenderer<RecoveryOrb> {
    public RecoveryOrbRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(RecoveryOrb orb, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        int color = orb.life() ? 0x82F0A0 : 0x73CFFF;
        var center = new Vector3f(0, .12f + .04f * (float)Math.sin((orb.tickCount + partial) * .15), 0);
        var out = buffers.getBuffer(SpiritEnergy.glow());
        SpiritEnergy.orb(out, pose.last(), center, .3f, color, .3f);
        SpiritEnergy.orb(out, pose.last(), center, .09f, color, .95f);
    }
    @Override public ResourceLocation getTextureLocation(RecoveryOrb orb) { return SpiritEnergy.SOFT_GLOW; }
}
