package cn.wwl.orimod.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Defaults belong to KeyMapping; options.txt belongs to the player. Never rewrites either mod's bindings. */
final class SpiritKeyDefaults {
    private static final SystemToast.SystemToastId NOTICE = new SystemToast.SystemToastId(15000);
    private static boolean checked, notified;
    private static Object lastScreen;
    record Conflict(KeyMapping first, KeyMapping second) {}
    private SpiritKeyDefaults() {}

    static List<Conflict> conflicts(KeyMapping[] mappings) {
        var result = new ArrayList<Conflict>();
        for (int i = 0; i < mappings.length; i++) for (int j = i + 1; j < mappings.length; j++) {
            var a = mappings[i]; var b = mappings[j];
            if (!a.isUnbound() && !b.isUnbound()
                    && (a.getName().startsWith("key.orimod.") || b.getName().startsWith("key.orimod."))
                    && a.same(b)) result.add(new Conflict(a, b));
        }
        return result;
    }

    static void tick(Minecraft mc) {
        boolean returned = lastScreen != null && mc.screen == null;
        lastScreen = mc.screen;
        if (notified || mc.player == null || checked && !returned) return;
        checked = true;
        var conflicts = conflicts(mc.options.keyMappings);
        if (conflicts.isEmpty()) return;
        var message = Component.empty();
        for (var c : conflicts) {
            if (!message.getSiblings().isEmpty()) message.append("\n");
            message.append(Component.translatable("message.orimod.key_conflict_pair", c.first.getTranslatedKeyMessage(),
                    Component.translatable(c.first.getName()), Component.translatable(c.second.getName())));
        }
        message.append("\n").append(Component.translatable("message.orimod.key_conflict_help"));
        mc.getToasts().addToast(SystemToast.multiline(mc, NOTICE,
                Component.translatable("message.orimod.key_conflict_title"), message));
        cn.wwl.orimod.OriMod.LOGGER.info("Key binding conflict notice: {}", message.getString());
        notified = true;
    }
}
