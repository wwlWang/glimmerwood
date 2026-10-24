package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;

/** Depth-tested translucent colour, no depth write and no bloom/additive whitening. */
public final class GinsoWaterRenderType extends RenderStateShard {
    public static final RenderType WATER=RenderType.create("orimod_ginso_personal_water",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,8192,false,true,
            RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private GinsoWaterRenderType(){super("orimod_ginso_personal_water",()->{},()->{});}
}
