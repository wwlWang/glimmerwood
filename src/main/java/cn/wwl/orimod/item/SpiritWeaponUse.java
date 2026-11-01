package cn.wwl.orimod.item;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.combat.SpiritSpear;
import cn.wwl.orimod.progression.SpiritProgression;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared resource preflight; authoritative spending stays in each server action. */
public final class SpiritWeaponUse {
    private SpiritWeaponUse() {}
    public static float cost(ItemStack stack) {
        return stack.is(OriItems.SPIRIT_SPEAR) ? SpiritSpear.COST
                : stack.is(OriItems.SPIRIT_HAMMER) ? SpiritHammerItem.COST
                : stack.is(OriItems.SPIRIT_BOW) ? SpiritCombat.BOW_COST : 0;
    }
    public static boolean blocked(float cost, float energy, boolean cooldown) { return cost > 0 && (energy < cost || cooldown); }
    public static boolean blocked(Player player, ItemStack stack) {
        return blocked(cost(stack), SpiritProgression.energyForUse(player), player.getCooldowns().isOnCooldown(stack.getItem()));
    }

    /** One refusal cue per held press, independent of vanilla's repeated use attempts. */
    public static final class FeedbackLatch {
        private boolean notified;
        public boolean refuse() { if (notified) return false; notified = true; return true; }
        public void release() { notified = false; }
    }
}
