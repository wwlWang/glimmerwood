package cn.wwl.orimod.client;

import cn.wwl.orimod.entity.GloomHeart;
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

/** R50: Gloom Heart look from vanilla block models: a tumbling husk, a core that swells on each telegraph
 *  (and burns red in phase 2), and four orbiting shards. */
public final class GloomHeartRenderer extends EntityRenderer<GloomHeart> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;

    public GloomHeartRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = 1.4f; }

    private void cube(BlockState state, PoseStack pose, MultiBufferSource buffers, float scale) {
        pose.pushPose(); pose.scale(scale, scale, scale); pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override public void render(GloomHeart e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partial;
        boolean charging = e.attack() != GloomHeart.IDLE;
        float swell = charging ? 1 + .35f * (1 - e.timer() / (float) GloomHeart.telegraph(e.attack())) : 1;
        pose.pushPose();
        pose.translate(0, 1.2, 0);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 2)); pose.mulPose(Axis.XP.rotationDegrees(30 + Mth.sin(t * .05f) * 15)); pose.mulPose(Axis.ZP.rotationDegrees(40));
        cube(Blocks.CRYING_OBSIDIAN.defaultBlockState(), pose, buffers, 2f * (e.hurtTime > 0 ? 1.08f : 1));
        pose.popPose();
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-t * 5));
        cube((e.phase() >= 2 ? Blocks.SHROOMLIGHT : Blocks.AMETHYST_BLOCK).defaultBlockState(), pose, buffers, 1.15f * swell);
        pose.popPose();
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (e.phase() >= 2 ? 7 : 4) + i * 90));
            pose.translate(2.3, Mth.sin(t * .1f + i) * .4, 0); pose.mulPose(Axis.XP.rotationDegrees(t * 6));
            cube(Blocks.AMETHYST_BLOCK.defaultBlockState(), pose, buffers, .6f);
            pose.popPose();
        }
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(GloomHeart e) { return InventoryMenu.BLOCK_ATLAS; }
}
