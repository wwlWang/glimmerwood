package cn.wwl.orimod.mixin;

import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerDimensionsMixin {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void orimod$dimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> result) {
        if (SpiritData.isSpirit((Player) (Object) this)) {
            result.setReturnValue(SpiritForm.dimensions(pose, result.getReturnValue()));
        }
    }
}
