package cn.wwl.orimod.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;

/** Prefer the dimension owner's translation; unknown dimensions still have a readable name. */
public final class DimensionNames {
    private DimensionNames() {}
    public static Component name(String id) {
        var location = ResourceLocation.tryParse(id);
        if (location == null) return Component.literal(fallback(id));
        String key = switch (id) {
            case "minecraft:overworld" -> "flat_world_preset.minecraft.overworld";
            case "minecraft:the_nether" -> "advancements.nether.root.title";
            case "minecraft:the_end" -> "biome.minecraft.the_end";
            default -> "dimension." + location.getNamespace() + "." + location.getPath().replace('/', '.');
        };
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(fallback(location.getPath()));
    }
    static String fallback(String path) {
        var words = path.replace('_', ' ').replace('/', ' ').replace('-', ' ').trim().split("\\s+");
        for (int i = 0; i < words.length; i++) if (!words[i].isEmpty())
            words[i] = words[i].substring(0, 1).toUpperCase(Locale.ROOT) + words[i].substring(1);
        return String.join(" ", words);
    }
}
