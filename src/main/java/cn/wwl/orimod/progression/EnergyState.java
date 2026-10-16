package cn.wwl.orimod.progression;

/** Session timers; pending healing is cancelled on form, equipment and dimension changes. */
public final class EnergyState {
    public long lastSpent, lastHurt, healReadyAt;
    public boolean pendingHeal;
}
