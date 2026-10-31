package cn.wwl.orimod.progression;

/** Session timers; pending healing is cancelled on form, equipment and dimension changes. */
public final class EnergyState {
    public boolean blightInside;
    public final ExplorationShardRules.Breath breath = new ExplorationShardRules.Breath();
    public long prospectorAt = Long.MIN_VALUE;
    public long lastSpent, lastHurt;
    public long recoveryHurtAt = Long.MIN_VALUE, recoveryLastTick = Long.MIN_VALUE;
    public final Regenerate.Session regenerate = new Regenerate.Session();
    /** R97: separate hint timers avoid treating a fresh player's tick zero as an energy spend. */
    public long lastHintSpendAt = Long.MIN_VALUE, lastCompanionHintAt = Long.MIN_VALUE;
    public boolean pendingHeal;
    /** R102: one pending line, speaking cooldowns and observed condition edges; never saved. */
    public final CompanionLines.Session lines = new CompanionLines.Session();
    /** R101: unsaved tide participation, kill count and once-per-day dawn settlement. */
    public final cn.wwl.orimod.world.GloomTideRules.NightLedger tide = new cn.wwl.orimod.world.GloomTideRules.NightLedger();
    /** R98: unsaved leases for sustained Flash and the charge-flame input that excludes it. */
    public long flashUntil = Long.MIN_VALUE, flashActiveSince = Long.MIN_VALUE, flashLastTick = Long.MIN_VALUE,
            chargeFlameUntil = Long.MIN_VALUE;
    public long flashNoticeAt;
    /** R99: rolling pickup healing and per-attacker retaliation cooldowns, never saved. */
    public final ShardEffects.HarvestWindow lifeHarvest = new ShardEffects.HarvestWindow();
    public final java.util.Map<java.util.UUID, Long> thornHits = new java.util.HashMap<>();
}
