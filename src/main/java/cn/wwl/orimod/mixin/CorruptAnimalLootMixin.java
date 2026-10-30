package cn.wwl.orimod.mixin;

import cn.wwl.orimod.entity.CorruptedAnimals;
import cn.wwl.orimod.item.OriItems;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only loot-table products; never filter mixed drops containing a horse's inventory. */
@Mixin(Mob.class)
public abstract class CorruptAnimalLootMixin {
    @Inject(method="dropFromLootTable",at=@At("HEAD"),cancellable=true)
    private void blightedProducts(DamageSource source,boolean recentlyHit,CallbackInfo ci){
        if((Object)this instanceof Animal a&&CorruptedAnimals.replaceLoot(a)){a.spawnAtLocation(new ItemStack(OriItems.GLOOM_RESIDUE.get()));ci.cancel();}
    }
}
