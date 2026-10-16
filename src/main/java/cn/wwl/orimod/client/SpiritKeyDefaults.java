package cn.wwl.orimod.client;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.config.OriClientConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Apply the bundled layout once. Later settings and non-default Iris bindings are left alone. */
final class SpiritKeyDefaults {
    private static Component notice;
    private SpiritKeyDefaults() {}

    static void tick(Minecraft mc) {
        if (!OriClientConfig.SPEC.isLoaded()) return;
        if (!OriClientConfig.KEY_DEFAULTS_APPLIED.get()) {
            for (KeyMapping mapping : mc.options.keyMappings) {
                if (!mapping.getName().equals("iris.keybind.reload") || !mapping.isDefault()
                        || !SpiritControls.BASH.isDefault() || !mapping.getKey().equals(SpiritControls.BASH.getKey())) continue;
                for (int code : new int[]{GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9, GLFW.GLFW_KEY_F10}) {
                    var replacement = InputConstants.Type.KEYSYM.getOrCreate(code);
                    if (java.util.Arrays.stream(mc.options.keyMappings).anyMatch(key -> key.getKey().equals(replacement))) continue;
                    mapping.setKeyModifierAndCode(KeyModifier.NONE, replacement);
                    KeyMapping.resetMapping();
                    mc.options.save();
                    notice = Component.translatable("message.orimod.renderer_key_moved", mapping.getTranslatedKeyMessage());
                    OriMod.LOGGER.info("Bundled key layout: moved default Iris reload binding to {}", replacement.getName());
                    break;
                }
            }
            OriClientConfig.KEY_DEFAULTS_APPLIED.set(true);
            OriClientConfig.SPEC.save();
        }
        if (notice != null && mc.player != null) {
            mc.player.sendSystemMessage(notice);
            notice = null;
        }
    }
}
