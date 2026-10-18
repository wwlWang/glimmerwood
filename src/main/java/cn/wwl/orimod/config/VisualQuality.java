package cn.wwl.orimod.config;

/**
 * Mod-owned effect quality tiers. They scale effects that this mod emits and pick the profile of the
 * bundled Spirit Glow pack ({@link SpiritGlowProfile}); other shader packs, vanilla video settings and
 * other mods keep their own controls.
 */
public enum VisualQuality implements net.neoforged.neoforge.common.TranslatableEnum {
    FLUENT(0.5), BALANCED(1.0), DETAILED(1.5);

    private final double particleScale;
    VisualQuality(double particleScale) { this.particleScale = particleScale; }

    /** Multiplier applied to the decorative part of each spirit effect burst. BALANCED keeps the verified counts. */
    public double particleScale() { return particleScale; }

    /** Label in the settings screen; the file keeps the stable enum name. */
    @Override public net.minecraft.network.chat.Component getTranslatedName() {
        return net.minecraft.network.chat.Component.translatable("orimod.quality." + name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Unknown or missing names fall back to the default tier instead of failing the whole client config. */
    public static VisualQuality parse(String name) {
        if (name != null) for (var quality : values()) if (quality.name().equals(name)) return quality;
        return BALANCED;
    }
}
