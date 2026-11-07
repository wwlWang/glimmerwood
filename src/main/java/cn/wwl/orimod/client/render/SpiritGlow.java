package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.config.SpiritGlowProfile;
import cn.wwl.orimod.config.SpiritGlowState;
import cn.wwl.orimod.config.SpiritGlowSync;

/**
 * Settings toggle for the bundled Spirit Glow shader pack. Without Iris the toggle has no effect and
 * no Iris class is loaded; all Iris calls live in {@link SpiritGlowIris}. The toggle is off by default:
 * the pack is copied into Iris's folder only once it is turned on (or to update a copy already there),
 * and a first start only shows a hint.
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
    private static cn.wwl.orimod.config.VisualQuality appliedQuality;
    private static int profileWrites;
    private static java.util.List<String> playerOptions = java.util.List.of();

    private SpiritGlow() {}

    public static boolean available() { return IRIS; }

    /** Read the live Iris selection: the settings toggle may lag a pack switch by ten ticks. */
    static boolean active() { return IRIS && SpiritGlowIris.state().ours(); }
    /** Any Iris pack (Spirit Glow, Mellow, ...): their bloom stacks on our additive first-person weapons. */
    static boolean shadersOn() { return IRIS && SpiritGlowIris.state().enabled(); }

    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!IRIS || !OriClientConfig.SPEC.isLoaded() || ++ticks % CHECK_INTERVAL != 0) return;
        try {
            boolean configured = OriClientConfig.SPIRIT_GLOW.get();
            if (!installed && !installFailed && (configured || SpiritGlowIris.exists(SpiritGlowSync.PACK))) {
                installed = SpiritGlowIris.install();
                installFailed = !installed;
            }
            var iris = SpiritGlowIris.state();
            var file = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(SpiritGlowState.FILE);
            if (saved == null) saved = SpiritGlowState.load(file);
            var state = saved;
            boolean setupDone = state.setupDone();
            var action = SpiritGlowSync.decide(setupDone, configured, synced, iris);
            if (action == SpiritGlowSync.Action.ENABLE && !installed) action = SpiritGlowSync.Action.NONE;
            boolean active = SpiritGlowSync.PACK.equals(iris.pack()) && iris.enabled();
            var quality = OriClientConfig.QUALITY.get();
            // PERF04: written before ENABLE so the reload in apply() already builds the matching profile.
            if (action == SpiritGlowSync.Action.ENABLE || (active && quality != appliedQuality)) {
                boolean changed = applyProfile(quality);
                appliedQuality = quality;
                if (changed && active && action == SpiritGlowSync.Action.NONE) SpiritGlowIris.reload();
            }
            switch (action) {
                case ENABLE -> {
                    var previous = SpiritGlowSync.remember(iris);
                    // Always refresh previousPack so disable restores the pack from this enable, not a stale one.
                    state = state.withPrevious(previous != null ? previous : new SpiritGlowSync.IrisState("", false));
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
            if (!setupDone && action == SpiritGlowSync.Action.SAVE_OFF) hint("kept");
            else if (!setupDone && !configured) hint("available");
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

    /** Writes the pack options for the quality tier; options the player set in Iris are kept. */
    private static boolean applyProfile(cn.wwl.orimod.config.VisualQuality quality) throws java.io.IOException {
        var state = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(SpiritGlowProfile.FILE);
        var options = SpiritGlowIris.optionsFile();
        var plan = SpiritGlowProfile.plan(SpiritGlowProfile.read(options), SpiritGlowProfile.read(state), quality);
        if (plan.changed()) {
            SpiritGlowProfile.write(options, plan.options(), "Spirit Glow options; oriMod sets the quality profile, values changed here are kept");
            profileWrites++;
        }
        SpiritGlowProfile.write(state, plan.owned(), "oriMod: Spirit Glow options last set for the quality tier (empty = pack default)");
        playerOptions = SpiritGlowProfile.MANAGED.stream().filter(key -> !plan.owned().containsKey(key)).toList();
        OriMod.LOGGER.info("Spirit Glow: quality {} -> profile {}{}", quality, SpiritGlowProfile.profile(quality),
                plan.changed() ? " (options updated)" : "");
        return plan.changed();
    }

    /** Plan 10.1: said once on the first start: a pack the player chose is kept, or Spirit Glow is available but off. */
    private static void hint(String key) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.components.toasts.SystemToast.add(mc.getToasts(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                net.minecraft.network.chat.Component.translatable("orimod.spirit_glow." + key + ".title"),
                net.minecraft.network.chat.Component.translatable("orimod.spirit_glow." + key + ".body"));
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
        values.put("profile", appliedQuality == null ? null : SpiritGlowProfile.profile(appliedQuality));
        values.put("profileWrites", profileWrites);
        values.put("playerOptions", playerOptions);
        if (IRIS) values.put("irisError", SpiritGlowIris.storedError());
        return values;
    }
}
