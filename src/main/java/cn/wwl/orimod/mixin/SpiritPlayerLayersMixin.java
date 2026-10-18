package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritAppearance;
import cn.wwl.orimod.client.render.SpiritOutline;
import cn.wwl.orimod.client.render.SpiritPlayerVisual;
import cn.wwl.orimod.client.render.SpiritVanillaLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replace only the body mesh and attachment transforms. Native names, events and buffers remain. */
@Mixin(LivingEntityRenderer.class)
public abstract class SpiritPlayerLayersMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow protected M model;
    @Unique private SpiritPlayerVisual.Frame orimod$frame;
    @Unique private PoseStack orimod$initial;
    @Unique private MultiBufferSource orimod$buffers;
    @Unique private Player orimod$player;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void orimod$begin(T entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        orimod$frame = entity instanceof Player player && model instanceof PlayerModel<?> ? SpiritPlayerVisual.frame(player) : null;
        if (orimod$frame == null) return;
        orimod$player = (Player)entity;
        orimod$initial = new PoseStack(); orimod$initial.mulPose(poses.last().pose());
        orimod$buffers = buffers;
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void orimod$body(EntityModel<?> rendered, PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        if (orimod$frame == null) rendered.renderToBuffer(poses, vertices, light, overlay, color);
        else SpiritOutline.render(orimod$player, orimod$frame, orimod$initial, orimod$buffers);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void orimod$layer(RenderLayer layer, PoseStack poses, MultiBufferSource buffers, int light, Entity entity,
                              float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (orimod$frame == null) layer.render(poses, buffers, light, entity, swing, amount, partial, age, yaw, pitch);
        else SpiritVanillaLayers.render(layer, (PlayerModel<?>)model, orimod$player, orimod$frame, orimod$initial,
                buffers, light, swing, amount, partial, age, yaw, pitch);
    }

    @Inject(method = "getShadowRadius(Lnet/minecraft/world/entity/LivingEntity;)F", at = @At("RETURN"), cancellable = true)
    private void orimod$shadow(T entity, CallbackInfoReturnable<Float> cir) {
        if (entity instanceof Player player && SpiritAppearance.visible(player)) cir.setReturnValue(SpiritVanillaLayers.shadowRadius(player));
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void orimod$end(T entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        orimod$frame = null; orimod$initial = null; orimod$buffers = null; orimod$player = null;
    }
}
