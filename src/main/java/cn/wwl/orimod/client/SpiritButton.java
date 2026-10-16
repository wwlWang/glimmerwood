package cn.wwl.orimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Vanilla button input/narration with the spirit panel's restrained teal treatment. */
final class SpiritButton extends Button {
    static final int TEXT = 0xE4F5EF, MUTED = 0x9DB7B2, ACCENT = 0xA8E6D5;
    SpiritButton(int x, int y, int width, int height, Component label, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int background = !active ? 0xFF16292C : isHoveredOrFocused() ? 0xFF315A5A : 0xFF244348;
        g.fill(getX(), getY(), getRight(), getBottom(), background);
        if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFB6F6DF);
        else g.fill(getX(), getBottom() - 1, getRight(), getBottom(), active ? 0xFF5C8A82 : 0xFF263F42);
        renderScrollingString(g, Minecraft.getInstance().font, 3, active ? TEXT : MUTED);
    }
}
