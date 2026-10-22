package cn.wwl.orimod.mixin;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class SpiritPlayerFoodMixin {
    @Inject(method="canEat", at=@At("HEAD"), cancellable=true)
    private void noWastedFood(boolean alwaysEdible,CallbackInfoReturnable<Boolean> ci) {
        if(SpiritData.isSpirit((Player)(Object)this)&&!alwaysEdible)ci.setReturnValue(false);
    }
    @Inject(method="causeFoodExhaustion", at=@At("HEAD"), cancellable=true)
    private void noSpiritExhaustion(float amount, CallbackInfo ci) { if (SpiritData.isSpirit((Player)(Object)this)) ci.cancel(); }
    @Redirect(method="eat", at=@At(value="INVOKE", target="Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
    private void noSpiritNutrition(FoodData food, FoodProperties properties) {
        if (!SpiritData.isSpirit((Player)(Object)this)) food.eat(properties);
    }
    // Vanilla peaceful healing/refilling is outside FoodData.tick. Only bypass that branch, not other heal calls.
    @Redirect(method="aiStep", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"))
    private Difficulty noPeacefulSpiritRefill(Level level) {
        return SpiritData.isSpirit((Player)(Object)this) ? Difficulty.NORMAL : level.getDifficulty();
    }
}
