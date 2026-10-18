package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Vanilla hand shading with triangle topology, including Iris's smooth tangents. */
final class SpiritHandRenderType extends RenderStateShard {
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(texture ->
            RenderType.create("orimod_hand", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    65536, false, false, RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)));

    private SpiritHandRenderType() { super("orimod_hand", () -> { }, () -> { }); }

    static RenderType triangles(ResourceLocation texture) { return TYPES.apply(texture); }
}
