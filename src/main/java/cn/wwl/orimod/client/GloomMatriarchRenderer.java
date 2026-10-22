package cn.wwl.orimod.client;

import cn.wwl.orimod.entity.GloomMatriarch;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * R70: Gloom Matriarch from vanilla block models: a long dark body, wide two-segment wings, a tail fan and a crest.
 * The eyes and wing tips go red while she telegraphs; she pitches forward during a dive and sags while stunned.
 */
public final class GloomMatriarchRenderer extends EntityRenderer<GloomMatriarch> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public GloomMatriarchRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = 1.4f; }

    private void box(BlockState state, PoseStack pose, MultiBufferSource buffers, float sx, float sy, float sz) {
        pose.pushPose(); pose.scale(sx, sy, sz); pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override public void render(GloomMatriarch e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (cn.wwl.orimod.client.render.GloomCreatureVisual.hideVanilla(e)) { super.render(e, yaw, partial, pose, buffers, light); return; }
        float t = e.tickCount + partial;
        int a = e.attack();
        boolean warning = a == GloomMatriarch.DIVE_TELEGRAPH || a == GloomMatriarch.FEATHERS || a == GloomMatriarch.GUST;
        boolean diving = a == GloomMatriarch.DIVE, stunned = a == GloomMatriarch.STUNNED;
        float flap = diving ? -60 : stunned ? 25 : a == GloomMatriarch.GUST ? Mth.sin(t * 1.2f) * 50 : Mth.sin(t * (warning ? .7f : .3f)) * 30;
        var eye = (warning || diving ? Blocks.REDSTONE_BLOCK : Blocks.AMETHYST_BLOCK).defaultBlockState();
        pose.pushPose();
        pose.translate(0, .6, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, e.yRotO, e.getYRot())));
        if (diving) pose.mulPose(Axis.XP.rotationDegrees(40));
        if (stunned) pose.mulPose(Axis.ZP.rotationDegrees(15));
        box(Blocks.BLACK_CONCRETE.defaultBlockState(), pose, buffers, .9f, .8f, 2.2f);
        box(Blocks.CRYING_OBSIDIAN.defaultBlockState(), pose, buffers, .5f, .5f, .5f);
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * .4, .2, 0); pose.mulPose(Axis.ZP.rotationDegrees(side * flap)); pose.translate(side * 1.3, 0, 0);
            box(Blocks.BLACK_WOOL.defaultBlockState(), pose, buffers, 2.6f, .12f, 1.4f);
            pose.translate(side * 1.3, 0, 0); pose.mulPose(Axis.ZP.rotationDegrees(side * flap * .6f)); pose.translate(side * 1.1, 0, -.2);
            box(Blocks.PURPLE_WOOL.defaultBlockState(), pose, buffers, 2.2f, .1f, 1.0f);
            pose.translate(side * 1.1, 0, 0);
            box(eye, pose, buffers, .3f, .14f, .3f);
            pose.popPose();
        }
        pose.pushPose(); pose.translate(0, .1, -1.4); box(Blocks.PURPLE_WOOL.defaultBlockState(), pose, buffers, 1.4f, .1f, .9f); pose.popPose();
        pose.pushPose(); pose.translate(0, .45, 1.0); box(Blocks.AMETHYST_BLOCK.defaultBlockState(), pose, buffers, .25f, .5f, .25f); pose.popPose();
        for (int side = -1; side <= 1; side += 2) { pose.pushPose(); pose.translate(side * .25, .15, 1.1); box(eye, pose, buffers, .18f, .18f, .18f); pose.popPose(); }
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(GloomMatriarch e) { return InventoryMenu.BLOCK_ATLAS; }
}
