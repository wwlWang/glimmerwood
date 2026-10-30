package cn.wwl.orimod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import cn.wwl.orimod.combat.SpearCharge;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.progression.Ability;
import net.minecraft.world.level.Level;

import java.util.List;

/** Hold to raise like a trident; release throws once, with a four-tick minimum even for a tap. */
public final class SpiritSpearItem extends Item {
    public SpiritSpearItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (SpiritWeaponUse.blocked(player, stack) || !SpiritData.isSpirit(player) || !Ability.SPEAR.unlocked(player))
            return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer server && !SpearCharge.begin(server, hand)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.SPEAR; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player) SpearCharge.release(player, stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SpiritTooltips.add(lines, "tooltip.orimod.spear.flavor", "tooltip.orimod.spear", "tooltip.orimod.spirit_required");
    }
    @Override public Component getName(ItemStack stack) { return SpiritTooltips.name(super.getName(stack)); }
}
