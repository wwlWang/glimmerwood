package cn.wwl.orimod.client;

import cn.wwl.orimod.block.OriFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Uses the original corruption textures through the vanilla fluid mesh (including flowing faces). */
public final class GloomFluidVisual {
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.fromNamespaceAndPath("orimod", "block/gloom_water"); }
            @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.fromNamespaceAndPath("orimod", "block/gloom_water_side"); }
        }, OriFluids.GLOOM_TYPE.get());
    }
    public static void setup() {
        ItemBlockRenderTypes.setRenderLayer(OriFluids.GLOOM_WATER.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(OriFluids.FLOWING_GLOOM_WATER.get(), RenderType.translucent());
    }
    private GloomFluidVisual() {}
}
