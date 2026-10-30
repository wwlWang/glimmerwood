package cn.wwl.orimod.combat;

import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Transient server action state; inventory and progression remain vanilla/persistent attachments. */
public final class CombatState {
    public String mode = "idle", lastCancel = "";
    public int actionId, stage, bladeStarts, bladeHits, bowShots, cancellations;
    /** Air slashes that held the player up since the last landing. */
    public int airHolds;
    public long startedAt, nextBowAt;
    public double slamStartY = Double.NaN;
    public boolean buffered;
    public ItemStack weapon = ItemStack.EMPTY;
    public SpearCharge.Charge spear;
    public record ComboHit(int stage, net.minecraft.world.damagesource.DamageSource source) {}
    public final java.util.Map<UUID, ComboHit> comboHits = new java.util.HashMap<>();
    public final Set<UUID> hitTargets = new HashSet<>();
}
