package cn.wwl.orimod.client;

import cn.wwl.orimod.network.ChallengeActionPayload;
import cn.wwl.orimod.network.RuinOfferPayload;
import cn.wwl.orimod.world.RuinKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** The same normal right-click screen is used by every naturally generated shrine. */
public final class RuinScreen extends Screen {
    private final RuinOfferPayload offer;
    private int left, top, panelWidth, panelHeight, scroll, visibleLines;
    private final List<Line> lines = new ArrayList<>();
    private Component narration = Component.empty();
    public RuinScreen(RuinOfferPayload offer) { super(RuinKind.byId(offer.kind()).title()); this.offer = offer; }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        panelWidth = Math.min(410, width - 20); panelHeight = Math.min(230, height - 20);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        lines.clear(); narration = title.copy();
        paragraph(Component.translatable("challenge.orimod.route." + offer.kind()), 0xE4F5EF);
        paragraph(Component.translatable("challenge.orimod.rules", Component.keybind("key.orimod.panel")), 0xAFC6C2);
        paragraph(Component.translatable("challenge.orimod.rewards", offer.clears(), offer.bestTicks() == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.2f s", offer.bestTicks() / 20.0)), 0xAFC6C2);
        if (!offer.problem().isEmpty()) paragraph(Component.translatable("challenge.orimod.require." + offer.problem()), 0xFFD18A);
        visibleLines = Math.max(2, (panelHeight - 80) / 11);
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - visibleLines));
        var start = addRenderableWidget(new SpiritButton(left + 14, top + panelHeight - 32, (panelWidth - 42) / 2, 20,
                Component.translatable(offer.active() ? "challenge.orimod.exit" : "challenge.orimod.start"), button -> {
            PacketDistributor.sendToServer(new ChallengeActionPayload(offer.shrine(), !offer.active())); onClose();
        }));
        start.active = offer.active() || offer.problem().isEmpty();
        addRenderableWidget(new SpiritButton(left + 28 + (panelWidth - 42) / 2, top + panelHeight - 32, (panelWidth - 42) / 2, 20,
                Component.translatable("gui.back"), button -> onClose()));
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB00A161C);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xF0142C30);
        g.renderOutline(left, top, panelWidth, panelHeight, 0xFF5C8A82);
        g.drawString(font, title, left + 14, top + 13, 0xB6F6DF, false);
        for (int i = 0; i < visibleLines && i + scroll < lines.size(); i++) {
            var line = lines.get(i + scroll);
            g.drawString(font, line.text(), left + 14, top + 34 + i * 11, line.color(), false);
        }
        if (lines.size() > visibleLines) {
            int track = visibleLines * 11, thumb = Math.max(10, track * visibleLines / lines.size());
            int y = top + 34 + (track - thumb) * scroll / (lines.size() - visibleLines);
            g.fill(left + panelWidth - 9, top + 34, left + panelWidth - 7, top + 34 + track, 0xFF29454A);
            g.fill(left + panelWidth - 9, y, left + panelWidth - 7, y + thumb, 0xFF8CDAC8);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
    private void paragraph(Component text, int color) {
        narration = narration.copy().append(". ").append(text);
        for (var line : font.split(text, panelWidth - 30)) lines.add(new Line(line, color));
        lines.add(new Line(FormattedCharSequence.EMPTY, color));
    }
    @Override public Component getNarrationMessage() { return narration; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int)Math.signum(vertical), 0, Math.max(0, lines.size() - visibleLines));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int change = key == GLFW.GLFW_KEY_DOWN ? 1 : key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_PAGE_DOWN ? visibleLines : key == GLFW.GLFW_KEY_PAGE_UP ? -visibleLines : 0;
        if (change != 0) { scroll = Math.clamp(scroll + change, 0, Math.max(0, lines.size() - visibleLines)); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    private record Line(FormattedCharSequence text, int color) {}
}
