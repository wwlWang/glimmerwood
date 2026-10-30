package cn.wwl.orimod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world gameplay settings, synchronized through NeoForge server configuration. */
public final class OriServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue WAVE_INTERVAL, MAX_NEARBY;
    public static final ModConfigSpec.DoubleValue GINSO_ESCAPE_WATER_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue BLIGHT_ENABLED, BLIGHT_SPREAD, BLIGHT_LEGACY;
    public static final ModConfigSpec.IntValue BLIGHT_RADIUS,BLIGHT_WRITES;
    public static final ModConfigSpec.DoubleValue BLIGHT_MULTIPLIER,BLIGHT_DRAIN;
    public static final ModConfigSpec.BooleanValue BLIGHT_ANIMALS,BLIGHT_RECOVERY,BLIGHT_EXTRA_MOBS;
    public static final ModConfigSpec.IntValue BLIGHT_EXPOSURE,BLIGHT_ANIMAL_CAP;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("gloomTide");
        ENABLED = b.comment("Gloom tides on new-moon Overworld nights.").define("enabled", true);
        WAVE_INTERVAL = b.comment("Ticks between waves for each eligible spirit.").defineInRange("waveInterval", 200, 20, 24000);
        MAX_NEARBY = b.comment("Maximum living tide mobs within 48 blocks of each player.").defineInRange("maxNearby", 8, 1, 64);
        b.pop();
        GINSO_ESCAPE_WATER_SPEED_MULTIPLIER = b.comment("Water speed multiplier for new Ginso Escape 2 trees. Captured when the escape starts; old trees keep 0.25 blocks/s.")
                .defineInRange("ginsoEscapeWaterSpeedMultiplier", 1.0, 0.5, 2.0);
        b.push("blightEcology");
        BLIGHT_ENABLED=b.comment("Enable corruption ecology. Existing restoration work is always retained.").define("enabled",true);
        BLIGHT_SPREAD=b.comment("Allow loaded living cores to spread onto eligible natural terrain.").define("spreadEnabled",true);
        BLIGHT_LEGACY=b.comment("Also enable ecology for cores from existing saves when next loaded.").define("legacyCoresSpread",true);
        BLIGHT_RADIUS=b.comment("Maximum spread radius; lowering never discards existing terrain records.").defineInRange("maxRadius",48,13,64);
        BLIGHT_WRITES=b.comment("Global direct block writes per tick, across all dimensions and cores.").defineInRange("blockWriteBudget",32,1,32);
        BLIGHT_ANIMALS=b.define("corruptAnimals",true);
        BLIGHT_EXTRA_MOBS=b.comment("Occasional local Gloom creatures; shares the three-per-core defender cap.").define("extraMobs",true);
        BLIGHT_RECOVERY=b.define("suppressNaturalRecovery",true);
        BLIGHT_EXPOSURE=b.defineInRange("animalExposureTicks",200,20,24000);
        BLIGHT_ANIMAL_CAP=b.defineInRange("maxNewCorruptAnimalsPerCore",16,1,64);
        BLIGHT_DRAIN=b.defineInRange("energyDrainPerSecond",.5,0,5);
        BLIGHT_MULTIPLIER=b.comment("Loaded-time spread rate. No offline catch-up, always subject to shared budgets.").defineInRange("spreadMultiplier",1.0,.1,4.0);
        b.pop();
        SPEC = b.build();
    }
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    public static int interval() { return SPEC.isLoaded() ? WAVE_INTERVAL.get() : 200; }
    public static int maxNearby() { return SPEC.isLoaded() ? MAX_NEARBY.get() : 8; }
    public static double ginsoWaterMultiplier() { return SPEC.isLoaded() ? GINSO_ESCAPE_WATER_SPEED_MULTIPLIER.get() : 1.0; }
    public static boolean blightEnabled(){return !SPEC.isLoaded()||BLIGHT_ENABLED.get();}
    public static boolean blightSpreads(){return !SPEC.isLoaded()||BLIGHT_SPREAD.get();}
    public static boolean legacyCoresSpread(){return !SPEC.isLoaded()||BLIGHT_LEGACY.get();}
    public static int blightRadius(){return SPEC.isLoaded()?BLIGHT_RADIUS.get():48;}
    public static int blightWrites(){return SPEC.isLoaded()?BLIGHT_WRITES.get():32;}
    public static double blightMultiplier(){return SPEC.isLoaded()?BLIGHT_MULTIPLIER.get():1;}
    public static boolean blightAnimals(){return !SPEC.isLoaded()||BLIGHT_ANIMALS.get();}
    public static boolean blightExtraMobs(){return !SPEC.isLoaded()||BLIGHT_EXTRA_MOBS.get();}
    public static boolean blightRecovery(){return !SPEC.isLoaded()||BLIGHT_RECOVERY.get();}
    public static int blightExposure(){return SPEC.isLoaded()?BLIGHT_EXPOSURE.get():200;}
    public static int blightAnimalCap(){return SPEC.isLoaded()?BLIGHT_ANIMAL_CAP.get():16;}
    public static float blightDrain(){return SPEC.isLoaded()?BLIGHT_DRAIN.get().floatValue():.5f;}
    private OriServerConfig() {}
}
