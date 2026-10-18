package cn.wwl.orimod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OriClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CONFIG_VERSION;
    public static final ModConfigSpec.BooleanValue KEY_DEFAULTS_APPLIED;
    public static final ModConfigSpec.EnumValue<SpiritCameraView> SPIRIT_CAMERA;
    public static final ModConfigSpec.EnumValue<VisualQuality> QUALITY;
    public static final ModConfigSpec.BooleanValue ADVANCED_PARTICLES;
    public static final ModConfigSpec.IntValue SCREEN_SHAKE;
    public static final ModConfigSpec.IntValue SCREEN_FLASH;
    /** Plan default: shake is light; flash is moderate. */
    public static final int DEFAULT_SHAKE = 30, DEFAULT_FLASH = 40;
    /** Bookkeeping entries kept in the file but not offered in the settings screen. */
    public static final java.util.Set<String> HIDDEN_IN_MENU = java.util.Set.of("configVersion", "keyDefaultsApplied");

    static {
        var builder = new ModConfigSpec.Builder();
        CONFIG_VERSION = builder.comment("Schema version of this file. Managed by the mod; files without it are upgraded from v1.")
                .defineInRange("configVersion", ClientConfigMigration.LEGACY, 1, Integer.MAX_VALUE);
        KEY_DEFAULTS_APPLIED = builder.comment("One-time setup of the bundled key layout. Custom bindings are preserved.")
                .define("keyDefaultsApplied", false);
        // R19: an enum so the settings screen offers a cycle button; stored names are unchanged from the v1 text field.
        SPIRIT_CAMERA = builder.comment("Camera used in spirit form: FIRST_PERSON, THIRD_PERSON_BACK (default) or THIRD_PERSON_FRONT.",
                        "Switching the camera with F5 while in spirit form updates this value.")
                .defineEnum("spiritCamera", SpiritCameraView.THIRD_PERSON_BACK);
        builder.push("visuals");
        QUALITY = builder.comment("Spirit effect quality: FLUENT, BALANCED (default) or DETAILED. Scales only particles emitted by this mod;",
                        "the shader pack and vanilla video settings are unchanged.")
                .defineEnum("quality", VisualQuality.BALANCED);
        ADVANCED_PARTICLES = builder.comment("Decorative spirit particles. When off, only the few particles that mark hits, landings,",
                        "arrows and challenge routes are kept, so gameplay feedback stays readable. Abilities are never disabled.")
                .define("advancedParticles", true);
        builder.pop();
        builder.push("feedback");
        SCREEN_SHAKE = builder.comment("Camera shake strength in percent when hurt or landing a stomp. 0 disables it.")
                .defineInRange("screenShake", DEFAULT_SHAKE, 0, 100);
        SCREEN_FLASH = builder.comment("Screen flash strength in percent when hurt in spirit form. 0 disables it.")
                .defineInRange("screenFlash", DEFAULT_FLASH, 0, 100);
        builder.pop();
        SPEC = builder.build();
    }
    private OriClientConfig() {}

    /** Safe snapshot for gameplay code; defaults when the client config is not loaded (e.g. early startup). */
    public record Visuals(VisualQuality quality, boolean advancedParticles, int screenShake, int screenFlash) {
        public static final Visuals DEFAULTS = new Visuals(VisualQuality.BALANCED, true, DEFAULT_SHAKE, DEFAULT_FLASH);
    }

    public static Visuals visuals() {
        if (!SPEC.isLoaded()) return Visuals.DEFAULTS;
        return new Visuals(QUALITY.get(), ADVANCED_PARTICLES.get(), SCREEN_SHAKE.get(), SCREEN_FLASH.get());
    }

    /** Called on config load/reload: records the schema upgrade once and saves the corrected file. */
    public static void upgrade(org.slf4j.Logger log) {
        if (!SPEC.isLoaded()) return;
        var plan = ClientConfigMigration.plan(CONFIG_VERSION.get());
        plan.notes().forEach(note -> log.info("Client config: {}", note));
        if (plan.changed()) { CONFIG_VERSION.set(plan.to()); SPEC.save(); }
    }
}
