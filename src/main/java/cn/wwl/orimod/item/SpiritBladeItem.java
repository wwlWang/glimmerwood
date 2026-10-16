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
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.orimod.blade_combo").withStyle(net.minecraft.ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.orimod.blade_cancel").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.orimod.spirit_required").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
