package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.LumenSight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public abstract class LumenLightmapMixin {
    /** Extend NeoForge's lightmap hook; vanilla night vision, darkness and gamma still run afterwards. */
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;adjustLightmapColors(Lnet/minecraft/client/multiplayer/ClientLevel;FFFFIILorg/joml/Vector3f;)V"))
    private void orimod$lumen(DimensionSpecialEffects effects, ClientLevel level, float partial, float skyDarken,
                              float flicker, float skyLight, int x, int y, Vector3f colors) {
        effects.adjustLightmapColors(level, partial, skyDarken, flicker, skyLight, x, y, colors);
        LumenSight.brighten(colors);
    }
}
