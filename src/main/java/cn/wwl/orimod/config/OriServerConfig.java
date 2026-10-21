package cn.wwl.orimod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world tide settings. No client appearance or unrelated gameplay settings live here. */
public final class OriServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue WAVE_INTERVAL, MAX_NEARBY;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("gloomTide");
        ENABLED = b.comment("Gloom tides on new-moon Overworld nights.").define("enabled", true);
        WAVE_INTERVAL = b.comment("Ticks between waves for each eligible spirit.").defineInRange("waveInterval", 200, 20, 24000);
        MAX_NEARBY = b.comment("Maximum living tide mobs within 48 blocks of each player.").defineInRange("maxNearby", 8, 1, 64);
        b.pop();
        SPEC = b.build();
    }
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    public static int interval() { return SPEC.isLoaded() ? WAVE_INTERVAL.get() : 200; }
    public static int maxNearby() { return SPEC.isLoaded() ? MAX_NEARBY.get() : 8; }
    private OriServerConfig() {}
}
