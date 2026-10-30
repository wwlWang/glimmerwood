package cn.wwl.orimod.mixin;

import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chicken.class)
public abstract class CorruptChickenMixin {
    @Shadow public int eggTime;
    @Inject(method="aiStep",at=@At("HEAD"))
    private void noCorruptedEggs(CallbackInfo ci){if(cn.wwl.orimod.entity.CorruptedAnimals.infected((Chicken)(Object)this)&&eggTime<=1)eggTime=2;}
}
