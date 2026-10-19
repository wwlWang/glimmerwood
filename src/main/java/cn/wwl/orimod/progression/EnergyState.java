package cn.wwl.orimod.progression;

/** Session timers; pending healing is cancelled on form, equipment and dimension changes. */
public final class EnergyState {
    /** R34: game time when the next Soul Link may be made. */
    public long soulLinkReadyAt;
    /** R37: game time when the companion may fire the next Spirit Flame. */
    public long flameReadyAt;
    public long lastSpent, lastHurt, healReadyAt;
    public boolean pendingHeal;
}
