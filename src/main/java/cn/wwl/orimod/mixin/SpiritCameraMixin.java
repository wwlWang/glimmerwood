package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third-person damped follow for the spirit form (see {@link SpiritCamera}). */
@Mixin(Camera.class)
public abstract class SpiritCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void orimod$follow(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (!detached || mirrored) { SpiritCamera.reset(); return; }
        Vec3 moved = SpiritCamera.follow(level, entity, getPosition(), partialTick);
        if (moved != null) setPosition(moved);
    }
}
