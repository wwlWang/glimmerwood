package cn.wwl.orimod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(RenderLayer.class)
public abstract class CorruptAnimalLayersMixin {
    @ModifyArg(method="renderColoredCutoutModel",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),index=4)
    private static int corruptedWool(int color,@Local(argsOnly=true) LivingEntity entity){return cn.wwl.orimod.client.render.CorruptAnimalLayer.tint(entity,color);}
}
