package cn.wwl.orimod.client.render;

import cn.wwl.orimod.world.OriEntities;
import cn.wwl.orimod.world.PracticeTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class PracticeTargetRenderer extends EntityRenderer<PracticeTarget> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public PracticeTargetRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = .4f; }
    public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(OriEntities.PRACTICE_TARGET.get(), PracticeTargetRenderer::new); }
    @Override public ResourceLocation getTextureLocation(PracticeTarget entity) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(PracticeTarget entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, .7, 0);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partial) * 1.8f));
        pose.mulPose(Axis.ZP.rotationDegrees(45));
        pose.scale(.75f, .75f, .75f);
        pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(Blocks.AMETHYST_BLOCK.defaultBlockState(), pose, buffers, light, OverlayTexture.pack(0, entity.hurtTime > 0));
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
