package cn.wwl.orimod.progression;

/** Session timers; pending healing is cancelled on form, equipment and dimension changes. */
public final class EnergyState {
    /** R34: game time when the next Soul Link may be made. */
    public long soulLinkReadyAt;
    /** R37: game time when the companion may fire the next Spirit Flame. */
    public long flameReadyAt;
    /** R39: next Light Burst throw. */
    public long burstReadyAt, waterDashReadyAt, chargeFlameReadyAt, spearReadyAt;
    /** R41: next Spirit Well refill. */
    public long wellReadyAt;
    public long lastSpent, lastHurt, healReadyAt;
    /** R97: separate hint timers avoid treating a fresh player's tick zero as an energy spend. */
    public long lastHintSpendAt = Long.MIN_VALUE, lastCompanionHintAt = Long.MIN_VALUE;
    public boolean pendingHeal;
    /** R101: unsaved tide participation, kill count and once-per-day dawn settlement. */
    public final cn.wwl.orimod.world.GloomTideRules.NightLedger tide = new cn.wwl.orimod.world.GloomTideRules.NightLedger();
    /** R98: unsaved leases and cooldown for sustained Flash and the charge-flame input that excludes it. */
    public long flashUntil = Long.MIN_VALUE, flashActiveSince = Long.MIN_VALUE, flashLastTick = Long.MIN_VALUE,
            chargeFlameUntil = Long.MIN_VALUE;
    public long flashReadyAt, flashNoticeAt;
    /** R99: rolling pickup healing and per-attacker retaliation cooldowns, never saved. */
    public final ShardEffects.HarvestWindow lifeHarvest = new ShardEffects.HarvestWindow();
    public final java.util.Map<java.util.UUID, Long> thornHits = new java.util.HashMap<>();
}
