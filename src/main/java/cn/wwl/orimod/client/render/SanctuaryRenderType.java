package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** World-lit stone plus a separate emission pass; no first-person hand shader marker. */
final class SanctuaryRenderType extends RenderStateShard {
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(texture ->
            RenderType.create("orimod_sanctuary", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    65536, false, false, RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)));

    private SanctuaryRenderType() { super("orimod_sanctuary", () -> { }, () -> { }); }

    private static final Function<ResourceLocation, RenderType> EMISSION = Util.memoize(texture ->
            RenderType.create("orimod_sanctuary_emission", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    65536, false, false, RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            // PBR emission alpha stores paint coverage, not opacity. Match vanilla eyes: RGB + RGB.
                            .setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL)
                            // Prevent coplanar stone/emission depth fighting without writing a second depth surface.
                            .setLayeringState(POLYGON_OFFSET_LAYERING)
                            .setWriteMaskState(COLOR_WRITE).createCompositeState(false)));

    static RenderType emission(ResourceLocation texture) { return EMISSION.apply(texture); }

    static RenderType stone(ResourceLocation texture) { return TYPES.apply(texture); }
}
