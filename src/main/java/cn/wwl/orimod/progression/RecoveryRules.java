package cn.wwl.orimod.progression;

/** Pure amounts; never reduce an existing value that is already above the passive cap. */
public final class RecoveryRules {
    private RecoveryRules() {}
    public static float life(float health, float max, boolean spirit, long sinceDamage, float seconds) {
        if (!spirit || !Float.isFinite(health) || !Float.isFinite(max) || health <= 0 || max <= 0
                || sinceDamage < RecoveryTuning.LIFE_DELAY || seconds <= 0) return 0;
        return Math.max(0, Math.min(max * RecoveryTuning.LIFE_CAP - health, RecoveryTuning.LIFE_PER_SECOND * seconds));
    }
    public static float energy(float energy, float max, long sinceSpend, boolean busy, boolean recovery, float seconds) {
        if (!Float.isFinite(energy) || !Float.isFinite(max) || energy < 0 || max <= 0 || busy
                || sinceSpend < RecoveryTuning.ENERGY_DELAY || seconds <= 0) return 0;
        return Math.max(0, Math.min(Math.min(max, RecoveryTuning.ENERGY_FLOOR) - energy,
                (recovery ? RecoveryTuning.RECOVERY_PER_SECOND : RecoveryTuning.ENERGY_PER_SECOND) * seconds));
    }
    public static String refusal(boolean available, float health, float max, float energy, boolean busy, boolean interrupted) {
        if (!available) return "unavailable";
        if (interrupted) return "release";
        if (!Float.isFinite(health) || !Float.isFinite(max) || health <= 0 || health >= max) return "health";
        if (!Float.isFinite(energy) || energy < RecoveryTuning.CAST_COST) return "energy";
        return busy ? "busy" : null;
    }
    public static int orbGroups(float maxHealth) {
        return Float.isFinite(maxHealth) && maxHealth > 0
                ? Math.clamp((int)Math.ceil(maxHealth / 20), 1, RecoveryTuning.ORB_MAX_GROUPS) : 1;
    }
}
