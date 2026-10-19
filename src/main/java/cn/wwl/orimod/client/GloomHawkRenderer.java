package cn.wwl.orimod.client;

import cn.wwl.orimod.entity.GloomHawk;
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

/** R61: Gloom Hawk silhouette from vanilla block models: a dark body, two flapping wings and a burning eye. */
public final class GloomHawkRenderer extends EntityRenderer<GloomHawk> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public GloomHawkRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = .6f; }

    private void box(BlockState state, PoseStack pose, MultiBufferSource buffers, float sx, float sy, float sz, int light) {
        pose.pushPose(); pose.scale(sx, sy, sz); pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override public void render(GloomHawk e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partial;
        boolean diving = e.state() == GloomHawk.DIVE, warning = e.state() == GloomHawk.TELEGRAPH;
        float flap = diving ? -55 : Mth.sin(t * (warning ? .9f : .45f)) * 35;
        pose.pushPose();
        pose.translate(0, .4, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, e.yRotO, e.getYRot())));
        if (diving) pose.mulPose(Axis.XP.rotationDegrees(40));
        box(Blocks.BLACK_CONCRETE.defaultBlockState(), pose, buffers, .45f, .4f, 1f, LightTexture.FULL_BRIGHT);
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * .2, .1, 0); pose.mulPose(Axis.ZP.rotationDegrees(side * flap)); pose.translate(side * .75, 0, 0);
            box(Blocks.BLACK_WOOL.defaultBlockState(), pose, buffers, 1.5f, .08f, .7f, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.pushPose(); pose.translate(0, .1, .5);
        box((warning || diving ? Blocks.REDSTONE_BLOCK : Blocks.AMETHYST_BLOCK).defaultBlockState(), pose, buffers, .2f, .2f, .2f, LightTexture.FULL_BRIGHT);
        pose.popPose();
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(GloomHawk e) { return InventoryMenu.BLOCK_ATLAS; }
}
