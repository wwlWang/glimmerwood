package cn.wwl.orimod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OriClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue KEY_DEFAULTS_APPLIED;
    public static final ModConfigSpec.ConfigValue<String> SPIRIT_CAMERA;
    static {
        var builder = new ModConfigSpec.Builder();
        KEY_DEFAULTS_APPLIED = builder.comment("One-time setup of the bundled key layout. Custom bindings are preserved.")
                .define("keyDefaultsApplied", false);
        SPIRIT_CAMERA = builder.comment("Remembered camera used in spirit form.").define("spiritCamera", "THIRD_PERSON_BACK",
                value -> value instanceof String text && java.util.Set.of("FIRST_PERSON", "THIRD_PERSON_BACK", "THIRD_PERSON_FRONT").contains(text));
        SPEC = builder.build();
    }
    private OriClientConfig() {}
}
