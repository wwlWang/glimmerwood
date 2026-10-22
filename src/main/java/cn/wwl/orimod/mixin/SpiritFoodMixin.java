package cn.wwl.orimod.mixin;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Do not mutate the saved human food level, saturation, exhaustion or regeneration timer. */
@Mixin(FoodData.class)
public abstract class SpiritFoodMixin {
    @Inject(method="tick", at=@At("HEAD"), cancellable=true)
    private void spiritMetabolism(Player p, CallbackInfo ci) { if (SpiritData.isSpirit(p)) ci.cancel(); }
}
