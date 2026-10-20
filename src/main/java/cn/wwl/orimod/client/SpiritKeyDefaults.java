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
        if (!OriClientConfig.FLASH_KEY_DEFAULTS_APPLIED.get()) {
            var flash = SpiritControls.FLASH;
            if (flash.isDefault()) {
                int code = flashKey(candidate -> java.util.Arrays.stream(mc.options.keyMappings)
                        .anyMatch(key -> key != flash && key.getKeyModifier() == KeyModifier.NONE
                                && key.getKey().equals(InputConstants.Type.KEYSYM.getOrCreate(candidate))));
                var replacement = InputConstants.Type.KEYSYM.getOrCreate(code);
                if (!flash.getKey().equals(replacement)) {
                    flash.setKeyModifierAndCode(KeyModifier.NONE, replacement);
                    KeyMapping.resetMapping(); mc.options.save();
                    notice = Component.translatable("message.orimod.flash_key_moved", flash.getTranslatedKeyMessage());
                    OriMod.LOGGER.info("Flash default key conflict: selected {}", replacement.getName());
                }
            }
            OriClientConfig.FLASH_KEY_DEFAULTS_APPLIED.set(true); OriClientConfig.SPEC.save();
        }
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

    /** R98: keep H if free; move only our still-default binding, never another mod's or a user's custom binding. */
    static int flashKey(java.util.function.IntPredicate occupied) {
        for (int code : new int[]{GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_Y})
            if (!occupied.test(code)) return code;
        return GLFW.GLFW_KEY_UNKNOWN;
    }
}
