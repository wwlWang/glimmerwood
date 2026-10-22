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

    private static final Function<ResourceLocation, RenderType> EMISSION = Util.memoize(texture ->
            RenderType.create("orimod_hand_emission", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    65536, false, false, RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            // PBR emission alpha stores paint coverage, not opacity. Match vanilla eyes: RGB + RGB.
                            .setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL)
                            // GUI skinning runs on CPU; offset the matching GPU mesh depth by a fraction of a pixel.
                            .setLayeringState(POLYGON_OFFSET_LAYERING)
                            .setWriteMaskState(COLOR_WRITE).createCompositeState(false)));

    static RenderType emission(ResourceLocation texture) { return EMISSION.apply(texture); }

    static RenderType triangles(ResourceLocation texture) { return TYPES.apply(texture); }
}
