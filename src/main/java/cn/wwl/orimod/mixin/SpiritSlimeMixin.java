package cn.wwl.orimod.mixin;

import cn.wwl.orimod.movement.SlimeBounce;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SlimeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeBlock.class)
abstract class SpiritSlimeMixin {
    @Inject(method = "bounceUp", at = @At("TAIL"))
    private void orimod$boundedBounce(Entity entity, CallbackInfo ci) {
        if (entity instanceof Player player) SlimeBounce.bounced(player);
    }
}
