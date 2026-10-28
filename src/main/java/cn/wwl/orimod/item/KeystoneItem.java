package cn.wwl.orimod.item;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Normal item entity and pickup rules; lost keys are recovered through their own pedestal. */
public final class KeystoneItem extends Item {
    public KeystoneItem(Properties properties) { super(properties.fireResistant()); }
    @Override public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return source.is(DamageTypes.GENERIC_KILL);
    }
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.setUnlimitedLifetime();
        return false;
    }
}
