package cn.wwl.orimod.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface SpiritRenderLayersAccessor {
    @Accessor("layers") List<RenderLayer<?, ?>> orimod$layers();
}
