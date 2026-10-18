package cn.wwl.orimod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StuckInBodyLayer.class)
public interface SpiritStuckLayerAccessor {
    @Invoker("renderStuckItem")
    void orimod$renderStuck(PoseStack poses, MultiBufferSource buffers, int light, Entity entity,
                            float x, float y, float z, float partial);
}
