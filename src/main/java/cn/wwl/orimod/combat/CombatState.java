package cn.wwl.orimod.combat;

import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Transient server action state; inventory and progression remain vanilla/persistent attachments. */
public final class CombatState {
    public String mode = "idle", lastCancel = "";
    public int actionId, stage, bladeStarts, bladeHits, bowShots, cancellations;
    public long startedAt, nextBowAt;
    public boolean buffered;
    public ItemStack weapon = ItemStack.EMPTY;
    public final Set<UUID> hitTargets = new HashSet<>();
}
