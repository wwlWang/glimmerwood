package cn.wwl.orimod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Uses the shared combat timeline instead of vanilla attack-strength attributes. */
public final class SpiritBladeItem extends SwordItem {
    public SpiritBladeItem(Properties properties) { super(Tiers.IRON, properties); }
    // Preserve old Damage components and repair recipes; all normal wear (including mining) is zero.
    @Override public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount, T entity,
            java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) { return 0; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SpiritTooltips.add(lines, "tooltip.orimod.blade.flavor", "tooltip.orimod.blade_combo", "tooltip.orimod.blade_cancel",
                "tooltip.orimod.blade_targets", "tooltip.orimod.spirit_required");
    }
    @Override public Component getName(ItemStack stack) { return SpiritTooltips.name(super.getName(stack)); }
}
