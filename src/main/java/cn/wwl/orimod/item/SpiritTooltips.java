package cn.wwl.orimod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;

/** R88: spirit weapon tooltips read as condensed light: a soft italic flavour line, then the rules in a quiet blue-grey. */
public final class SpiritTooltips {
    public static final int NAME = 0xD6EEFF, FLAVOR = 0xA8DCFF, RULE = 0x8FA9C4;
    private SpiritTooltips() {}

    public static Component name(Component base) { return base.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(NAME))); }

    public static void add(List<Component> lines, String flavor, String... rules) {
        lines.add(Component.translatable(flavor).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FLAVOR)).withItalic(true)));
        for (String rule : rules) lines.add(Component.literal("· ").append(Component.translatable(rule)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(RULE))));
    }
}
