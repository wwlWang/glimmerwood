package cn.wwl.orimod.client;

import cn.wwl.orimod.entity.GloomSpitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

/**
 * R48: an original, simple Gloom Spitter silhouette built from vanilla block models (no new textures): a slowly
 * tumbling amethyst husk (R53: larger and brighter for distance) around a crying-obsidian core that turns into a
 * white sea-lantern core and swells while it charges an orb.
 */
public final class GloomSpitterRenderer extends EntityRenderer<GloomSpitter> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;

    public GloomSpitterRenderer(EntityRendererProvider.Context context) {
        super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = .4f;
    }

    /** Core scale for the telegraph: grows from .45 to .8 as the charge runs out. */
    public static float coreScale(int chargeLeft) {
        if (chargeLeft <= 0) return .45f;
        return .45f + .35f * (1 - chargeLeft / (float) GloomSpitter.CHARGE_TICKS);
    }

    @Override public void render(GloomSpitter e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partial;
        float hurt = e.hurtTime > 0 ? 1.15f : 1;
        pose.pushPose();
        pose.translate(0, .6 + Mth.sin(t * .12f) * .06, 0);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 3)); pose.mulPose(Axis.XP.rotationDegrees(35)); pose.mulPose(Axis.ZP.rotationDegrees(35));
        float body = .85f * hurt;
        pose.scale(body, body, body); pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(Blocks.AMETHYST_BLOCK.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        pose.pushPose();
        float core = coreScale(e.charge()) * hurt;
        pose.translate(0, 0, .0); pose.mulPose(Axis.YP.rotationDegrees(-t * 6));
        pose.scale(core, core, core); pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock((e.charge() > 0 ? Blocks.SEA_LANTERN : Blocks.CRYING_OBSIDIAN).defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(GloomSpitter e) { return InventoryMenu.BLOCK_ATLAS; }
}
