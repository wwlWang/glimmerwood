package cn.wwl.orimod.combat;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.item.SpiritWeaponUse;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Server-owned spear charge. A quick release waits out the minimum; it never spends on press. */
public final class SpearCharge {
    public static final int MIN_TICKS = 4;
    private SpearCharge() {}

    public static long releaseAt(long startedAt, long releasedAt) {
        return Math.max(startedAt + MIN_TICKS, releasedAt);
    }

    public static boolean deferRelease(int ticksUsingItem) { return ticksUsingItem < MIN_TICKS; }

    public static final class Charge {
        final ItemStack stack;
        final InteractionHand hand;
        final int slot;
        final long startedAt;
        long fireAt = Long.MAX_VALUE;
        Charge(ServerPlayer player, InteractionHand hand) {
            this.stack = player.getItemInHand(hand); this.hand = hand;
            this.slot = player.getInventory().selected; this.startedAt = player.level().getGameTime();
        }
    }

    public static boolean begin(ServerPlayer player, InteractionHand hand) {
        if (!canThrow(player, player.getItemInHand(hand))) return false;
        Flash.stop(player);
        player.getData(SpiritData.COMBAT).spear = new Charge(player, hand);
        return true;
    }

    private static boolean canThrow(ServerPlayer player, ItemStack stack) {
        return stack.is(OriItems.SPIRIT_SPEAR) && !SpiritWeaponUse.blocked(player, stack)
                && SpiritSpear.refusal(SpiritData.isSpirit(player), Ability.SPEAR.unlocked(player),
                    SpiritProgression.energyForUse(player), cn.wwl.orimod.progression.SpiritCooldowns.now(player),
                    player.getData(SpiritData.COOLDOWNS).spearReadyAt) == null;
    }

    private static boolean stillHolding(ServerPlayer player, Charge charge) {
        return player.isAlive() && !player.hasContainerOpen() && SpiritData.isSpirit(player)
                && player.getInventory().selected == charge.slot && player.getItemInHand(charge.hand) == charge.stack;
    }

    public static void release(ServerPlayer player, ItemStack stack) {
        var charge = player.getData(SpiritData.COMBAT).spear;
        if (charge == null) return;
        if (charge.stack != stack || !stillHolding(player, charge)) { cancel(player); return; }
        charge.fireAt = releaseAt(charge.startedAt, player.level().getGameTime());
        tick(player);
    }

    public static void tick(ServerPlayer player) {
        var state = player.getData(SpiritData.COMBAT);
        var charge = state.spear;
        if (charge == null) return;
        if (!stillHolding(player, charge) || charge.fireAt == Long.MAX_VALUE
                && (!player.isUsingItem() || player.getUseItem() != charge.stack)) { cancel(player); return; }
        if (player.level().getGameTime() < charge.fireAt) return;
        state.spear = null; // Consume this release before spawning; duplicate packets cannot fire again.
        if (canThrow(player, charge.stack) && SpiritSpear.thrown(player) != null)
            player.getCooldowns().addCooldown(charge.stack.getItem(), SpiritSpear.COOLDOWN_TICKS);
    }

    public static void cancel(ServerPlayer player) {
        player.getData(SpiritData.COMBAT).spear = null;
        if (player.isUsingItem() && player.getUseItem().is(OriItems.SPIRIT_SPEAR)) player.stopUsingItem();
    }
}
