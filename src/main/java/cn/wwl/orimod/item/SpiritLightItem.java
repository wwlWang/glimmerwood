package cn.wwl.orimod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class SpiritLightItem extends Item {
    public SpiritLightItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.orimod.light_sources").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.orimod.light_peaceful").withStyle(ChatFormatting.GRAY));
    }
}
