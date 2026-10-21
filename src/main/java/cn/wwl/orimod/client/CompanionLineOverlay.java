package cn.wwl.orimod.client;

import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.fx.CompanionLineDisplay;
import cn.wwl.orimod.progression.CompanionLines.Line;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.*;
import java.util.Map;

/** Independent line above actionbar/hints, wrapping upward on narrow windows without replacing functional messages. */
public final class CompanionLineOverlay {
    private static final CompanionLineDisplay DISPLAY = new CompanionLineDisplay();
    private static ClientLevel world;
    private static boolean eligible() {
        var p = Minecraft.getInstance().player;
        return p != null && p.isAlive() && !p.isSpectator() && SpiritData.isAwakened(p) && SpiritData.isSpirit(p) && OriClientConfig.companionHints();
    }
    public static void show(String id) {
        if (!eligible() || Line.byId(id) == null) return;
        world = Minecraft.getInstance().level; DISPLAY.show(id);
    }
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (world != mc.level || !eligible()) { DISPLAY.clear(); world = mc.level; }
        if (!mc.isPaused()) DISPLAY.tick();
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { DISPLAY.clear(); world = null; }
    private static Component text() { return DISPLAY.id().isEmpty() ? Component.empty() : Component.translatable("line.orimod." + DISPLAY.id()); }
    public static Map<String, Object> diagnostics() {
        return Map.of("id", DISPLAY.id(), "text", text().getString(), "remaining", DISPLAY.remaining());
    }
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.parse("orimod:companion_line"), (g, delta) -> {
            var mc = Minecraft.getInstance();
            if (!eligible() || world != mc.level || mc.options.hideGui || mc.screen != null || DISPLAY.remaining() <= 0) return;
            int alpha = DISPLAY.alpha(delta.getGameTimeDeltaPartialTick(false));
            if (alpha <= 3) return;
            var rows = mc.font.split(text(), Math.max(40, g.guiWidth() - 24));
            int bottom = g.guiHeight() - CompanionLineDisplay.BOTTOM_OFFSET;
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                g.drawString(mc.font, row, (g.guiWidth() - mc.font.width(row)) / 2,
                        bottom - (rows.size() - 1 - i) * CompanionLineDisplay.ROW_SPACING, alpha << 24 | 0xD5F1FF, true);
            }
        });
    }
    private CompanionLineOverlay() {}
}
