package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.fx.SpiritFxDefinition;
import cn.wwl.orimod.fx.SpiritFxKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** FX02: loads {@code assets/orimod/spirit_fx/<kind>.json} on every resource reload; missing or broken files use the built-in look. */
public final class SpiritFxResources implements ResourceManagerReloadListener {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static volatile Map<SpiritFxKind, SpiritFxDefinition> definitions = fallbacks();
    private static volatile Map<String, String> problems = Map.of();
    private static volatile int loaded, reloads;

    public static void register(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SpiritFxResources());
    }

    public static SpiritFxDefinition get(SpiritFxKind kind) { return definitions.get(kind); }

    public static Map<String, Object> diagnostics() {
        return Map.of("loaded", loaded, "reloads", reloads, "problems", problems);
    }

    @Override public void onResourceManagerReload(ResourceManager manager) {
        var map = fallbacks();
        var issues = new java.util.TreeMap<String, String>();
        int count = 0;
        for (var kind : SpiritFxKind.values()) {
            var id = ResourceLocation.fromNamespaceAndPath("orimod", "spirit_fx/" + SpiritFxDefinition.fileName(kind) + ".json");
            var resource = manager.getResource(id);
            if (resource.isEmpty()) { issues.put(kind.name(), "missing"); continue; }
            try (var reader = resource.get().openAsReader()) {
                map.put(kind, SpiritFxDefinition.parse(kind, com.google.gson.JsonParser.parseReader(reader).getAsJsonObject()));
                count++;
            } catch (Exception error) {
                issues.put(kind.name(), String.valueOf(error.getMessage()));
                LOG.warn("Spirit effect {} uses the built-in look: {}", id, error.getMessage());
            }
        }
        definitions = map; problems = Map.copyOf(issues); loaded = count; reloads++;
    }

    private static Map<SpiritFxKind, SpiritFxDefinition> fallbacks() {
        var map = new EnumMap<SpiritFxKind, SpiritFxDefinition>(SpiritFxKind.class);
        for (var kind : SpiritFxKind.values()) map.put(kind, SpiritFxDefinition.fallback(kind));
        return map;
    }
}
