package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * R88: soft additive spirit light without a shader pack. Eyes shader (texture x vertex colour, no lighting) with
 * src-alpha/one blending and no depth write, so overlapping soft sprites add up into a glow instead of stacking as
 * opaque blobs. Used by the body aura, the companion halo and the condensed-energy weapons.
 */
public final class SpiritEnergy extends RenderStateShard {
    public static final ResourceLocation SOFT_GLOW = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "textures/misc/soft_glow.png");
    public static final ResourceLocation STREAK = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "textures/misc/energy_streak.png");
    public static final ResourceLocation RESTORATION_COLUMN = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "textures/misc/restoration_column.png");
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(texture ->
            RenderType.create("orimod_spirit_energy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, true, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    private SpiritEnergy() { super("orimod_spirit_energy", () -> { }, () -> { }); }

    public static RenderType glow() { return TYPES.apply(SOFT_GLOW); }
    public static RenderType streak() { return TYPES.apply(STREAK); }
    public static RenderType restorationColumn() { return TYPES.apply(RESTORATION_COLUMN); }
    /** Soft falloff across u, constant along v: a ribbon whose edges fade (blade slashes reuse the column texture). */
    public static RenderType ribbon() { return TYPES.apply(RESTORATION_COLUMN); }

    /** One ribbon segment: {@code inner0..outer0} to {@code inner1..outer1}; u runs inner→outer, alpha per end. */
    public static void strip(VertexConsumer out, PoseStack.Pose pose, Vector3f inner0, Vector3f outer0, Vector3f inner1, Vector3f outer1,
                             int rgb, float alpha0, float alpha1) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        corner(out, pose, inner0.x, inner0.y, inner0.z, 0, 0, r, g, b, alpha0);
        corner(out, pose, outer0.x, outer0.y, outer0.z, 1, 0, r, g, b, alpha0);
        corner(out, pose, outer1.x, outer1.y, outer1.z, 1, 1, r, g, b, alpha1);
        corner(out, pose, inner1.x, inner1.y, inner1.z, 0, 1, r, g, b, alpha1);
    }

    /** A broad vertical column slice, crossed on X/Z, with continuous endpoint alpha. */
    public static void columnSlice(VertexConsumer out, PoseStack.Pose pose, Vector3f bottom, float height,
                                   float width, int rgb, float bottomAlpha, float topAlpha) {
        float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,b=(rgb&255)/255f;
        for(int axis=0;axis<2;axis++){
            float dx=axis==0?width/2:0,dz=axis==1?width/2:0;
            corner(out,pose,bottom.x-dx,bottom.y,bottom.z-dz,0,1,r,g,b,bottomAlpha);
            corner(out,pose,bottom.x+dx,bottom.y,bottom.z+dz,1,1,r,g,b,bottomAlpha);
            corner(out,pose,bottom.x+dx,bottom.y+height,bottom.z+dz,1,0,r,g,b,topAlpha);
            corner(out,pose,bottom.x-dx,bottom.y+height,bottom.z-dz,0,0,r,g,b,topAlpha);
        }
    }

    /** Seconds on a wrapping clock for pulses. */
    public static float time() { return (Util.getMillis() % 3_600_000L) / 1000f; }

    /** A quad centred at {@code c} spanned by half-axes {@code u} and {@code v}. */
    public static void quad(VertexConsumer out, PoseStack.Pose pose, Vector3f c, Vector3f u, Vector3f v, int rgb, float alpha) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        corner(out, pose, c.x - u.x - v.x, c.y - u.y - v.y, c.z - u.z - v.z, 0, 1, r, g, b, alpha);
        corner(out, pose, c.x + u.x - v.x, c.y + u.y - v.y, c.z + u.z - v.z, 1, 1, r, g, b, alpha);
        corner(out, pose, c.x + u.x + v.x, c.y + u.y + v.y, c.z + u.z + v.z, 1, 0, r, g, b, alpha);
        corner(out, pose, c.x - u.x + v.x, c.y - u.y + v.y, c.z - u.z + v.z, 0, 0, r, g, b, alpha);
    }

    private static void corner(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }

    /** A glow sprite seen from three axes (so it stays round from any angle without a camera billboard). */
    public static void orb(VertexConsumer out, PoseStack.Pose pose, Vector3f c, float radius, int rgb, float alpha) {
        quad(out, pose, c, new Vector3f(radius, 0, 0), new Vector3f(0, radius, 0), rgb, alpha);
        quad(out, pose, c, new Vector3f(radius, 0, 0), new Vector3f(0, 0, radius), rgb, alpha);
        quad(out, pose, c, new Vector3f(0, 0, radius), new Vector3f(0, radius, 0), rgb, alpha);
    }

    /** A soft energy ribbon from {@code a} to {@code b}, as two crossed planes, {@code width} across. */
    public static void beam(VertexConsumer out, PoseStack.Pose pose, Vector3f a, Vector3f b, float width, Vector3f side, int rgb, float alpha) {
        var c = new Vector3f(a).add(b).mul(.5f);
        var along = new Vector3f(b).sub(a).mul(.5f);
        var s1 = new Vector3f(side).normalize().mul(width * .5f);
        var s2 = new Vector3f(along).cross(side).normalize().mul(width * .5f);
        // The streak texture runs along v; u spans the width.
        quad(out, pose, c, s1, along, rgb, alpha);
        quad(out, pose, c, s2, along, rgb, alpha);
    }

    /** Camera-facing sprite in world space relative to the camera. */
    public static void billboard(VertexConsumer out, PoseStack.Pose pose, Vector3f c, org.joml.Quaternionf camera, float radius, int rgb, float alpha) {
        var u = camera.transform(new Vector3f(radius, 0, 0));
        var v = camera.transform(new Vector3f(0, radius, 0));
        quad(out, pose, c, u, v, rgb, alpha);
    }

    /** Large Flash bloom must fade before its camera-facing plane intersects the terrain depth buffer. */
    public static void billboardAboveGround(VertexConsumer out, PoseStack.Pose pose, Vector3f c,
                                           org.joml.Quaternionf camera, float radius, int rgb, float alpha, float floor) {
        var u = camera.transform(new Vector3f(radius, 0, 0));
        var v = camera.transform(new Vector3f(0, radius, 0));
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        for (var vertex : SoftGlowClip.vertices(c, u, v, floor, .45f))
            corner(out, pose, vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(), r, g, b, alpha * vertex.alpha());
    }
}
