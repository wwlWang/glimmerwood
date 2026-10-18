package cn.wwl.orimod.fx;

import cn.wwl.orimod.config.VisualQuality;

/** Pure particle budget for one spirit effect burst; no client classes so it is unit-testable. */
public final class FxBudget {
    /** Hard ceiling per burst so DETAILED cannot turn a stomp into a frame spike. */
    public static final int MAX_PER_BURST = 48;

    private FxBudget() {}

    /**
     * @param essential spawned with force: visible regardless of vanilla particle level
     * @param decorative spawned through the normal vanilla path, which may still thin them out
     */
    public record Split(int essential, int decorative) {
        public int total() { return essential + decorative; }
    }

    public static Split of(SpiritFxKind kind, VisualQuality quality, boolean advancedParticles) {
        int essential = Math.min(kind.essential(), kind.base());
        if (!advancedParticles) return new Split(essential, 0);
        long scaled = Math.round(kind.base() * quality.particleScale());
        int total = (int)Math.max(essential, Math.min(MAX_PER_BURST, scaled));
        return new Split(essential, total - essential);
    }
}
