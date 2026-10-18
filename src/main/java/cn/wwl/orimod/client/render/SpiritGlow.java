package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.config.SpiritGlowState;
import cn.wwl.orimod.config.SpiritGlowSync;

/**
 * Settings toggle for the bundled Spirit Glow shader pack. Without Iris the toggle has no effect and
 * no Iris class is loaded; all Iris calls live in {@link SpiritGlowIris}.
 */
public final class SpiritGlow {
    /** Bundled pack inside the mod jar, built from shaderpacks/spirit-glow by the zipSpiritGlow task. */
    public static final String RESOURCE = "/orimod_shaderpack/spirit_glow.zip";
    private static final int CHECK_INTERVAL = 10;
    private static final boolean IRIS = net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded("iris");
    private static Boolean synced;
    private static SpiritGlowState saved;
    private static boolean installed, installFailed, hinted;
    private static int ticks;
    private static String lastAction = "none", lastError;

    private SpiritGlow() {}

    public static boolean available() { return IRIS; }

    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!IRIS || !OriClientConfig.SPEC.isLoaded() || ++ticks % CHECK_INTERVAL != 0) return;
        try {
            if (!installed && !installFailed) {
                installed = SpiritGlowIris.install();
                installFailed = !installed;
            }
            var iris = SpiritGlowIris.state();
            var file = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(SpiritGlowState.FILE);
            if (saved == null) saved = SpiritGlowState.load(file);
            var state = saved;
            boolean setupDone = state.setupDone();
            boolean configured = OriClientConfig.SPIRIT_GLOW.get();
            var action = SpiritGlowSync.decide(setupDone, configured, synced, iris);
            if (action == SpiritGlowSync.Action.ENABLE && !installed) action = SpiritGlowSync.Action.NONE;
            switch (action) {
                case ENABLE -> {
                    var previous = SpiritGlowSync.remember(iris);
                    if (previous != null) state = state.withPrevious(previous);
                    SpiritGlowIris.apply(SpiritGlowSync.PACK, true);
                }
                case DISABLE -> {
                    var restore = SpiritGlowSync.restore(state.previousPack(), state.previousEnabled());
                    // The remembered pack may have been deleted meanwhile; turn shaders off rather than select a missing pack.
                    if (restore.pack() != null && !SpiritGlowIris.exists(restore.pack())) restore = SpiritGlowSync.restore("", false);
                    SpiritGlowIris.apply(restore.pack(), restore.enabled());
                }
                case SAVE_ON -> OriClientConfig.SPIRIT_GLOW.set(true);
                case SAVE_OFF -> OriClientConfig.SPIRIT_GLOW.set(false);
                case NONE -> { }
            }
            if (action != SpiritGlowSync.Action.NONE) {
                lastAction = action.name().toLowerCase(java.util.Locale.ROOT);
                OriMod.LOGGER.info("Spirit Glow: {} (Iris pack {}, shaders {})", lastAction, iris.pack(), iris.enabled());
            }
            if (!setupDone && action == SpiritGlowSync.Action.SAVE_OFF) hintKeptPack();
            if (!setupDone) state = state.withSetupDone();
            if (!state.equals(saved)) { state.save(file); saved = state; }
            if (action == SpiritGlowSync.Action.SAVE_ON || action == SpiritGlowSync.Action.SAVE_OFF) OriClientConfig.SPEC.save();
            synced = OriClientConfig.SPIRIT_GLOW.get();
        } catch (Exception | LinkageError error) {
            lastError = error.toString();
            OriMod.LOGGER.warn("Spirit Glow: could not update the shader pack", error);
            installFailed = true;
            synced = OriClientConfig.SPIRIT_GLOW.get();
        }
    }

    /** Plan 10.1: a pack the player already chose is kept; say once that Spirit Glow can be switched on. */
    private static void hintKeptPack() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.components.toasts.SystemToast.add(mc.getToasts(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                net.minecraft.network.chat.Component.translatable("orimod.spirit_glow.kept.title"),
                net.minecraft.network.chat.Component.translatable("orimod.spirit_glow.kept.body"));
        hinted = true;
    }

    public static java.util.Map<String, Object> diagnostics() {
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("iris", IRIS);
        values.put("installed", installed);
        values.put("toggle", OriClientConfig.SPEC.isLoaded() ? OriClientConfig.SPIRIT_GLOW.get() : null);
        values.put("setupDone", saved == null ? null : saved.setupDone());
        values.put("previousPack", saved == null ? null : saved.previousPack());
        values.put("lastAction", lastAction);
        values.put("keptPackHint", hinted);
        values.put("lastError", lastError);
        if (IRIS) values.put("irisError", SpiritGlowIris.storedError());
        return values;
    }
}
