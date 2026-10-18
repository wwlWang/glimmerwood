package cn.wwl.orimod.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Version bookkeeping for {@code orimod-client.toml}. NeoForge's spec correction already fills missing
 * optional values with defaults and keeps every valid known value; this class records which schema the
 * file came from so later changes have an explicit, logged upgrade step.
 */
public final class ClientConfigMigration {
    /** v1: keyDefaultsApplied + spiritCamera only (R1–R13). v2: R15 visual and feedback settings. */
    public static final int CURRENT = 2;
    /** Default written for a missing field, so files created before versioning are recognised as v1. */
    public static final int LEGACY = 1;

    private ClientConfigMigration() {}

    public record Plan(int from, int to, List<String> notes) {
        public boolean changed() { return from != to; }
    }

    public static Plan plan(int stored) {
        var notes = new ArrayList<String>();
        if (stored > CURRENT) {
            // Written by a newer build: keep the number so a later downgrade/upgrade cycle does not lose it.
            notes.add("config written by newer schema v" + stored + "; unknown fields keep defaults, version left unchanged");
            return new Plan(stored, stored, List.copyOf(notes));
        }
        int from = Math.max(stored, LEGACY);
        if (from < 2) notes.add("v1 -> v2: added quality, advancedParticles, screenShake, screenFlash with defaults; key layout and spirit camera kept");
        return new Plan(stored, CURRENT, List.copyOf(notes));
    }
}
