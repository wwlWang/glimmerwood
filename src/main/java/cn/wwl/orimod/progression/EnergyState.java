package cn.wwl.orimod.progression;

/** Session timers; pending healing is cancelled on form, equipment and dimension changes. */
public final class EnergyState {
    /** R34: game time when the next Soul Link may be made. */
    public long soulLinkReadyAt;
    public long lastSpent, lastHurt, healReadyAt;
    public boolean pendingHeal;
}
