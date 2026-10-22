package cn.wwl.orimod.item;

import cn.wwl.orimod.combat.SpiritCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SpiritBowItem extends BowItem {
    public SpiritBowItem(Properties properties) { super(properties); }

    // Preserve old Damage components and repair recipes; all normal wear (including mining) is zero.
    @Override public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount, T entity,
            java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) { return 0; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.orimod.bow_energy").withStyle(net.minecraft.ChatFormatting.AQUA));
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.orimod.bow_charge").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.orimod.bow_volley").withStyle(net.minecraft.ChatFormatting.AQUA));
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.orimod.spirit_required").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!SpiritCombat.canUseBow(player) || player instanceof ServerPlayer server && !SpiritCombat.beginBow(server, stack)) {
            if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    !cn.wwl.orimod.spirit.SpiritData.isSpirit(player) ? "combat.orimod.form_required"
                    : !cn.wwl.orimod.progression.Ability.BOW.unlocked(player) ? "combat.orimod.bow_locked"
                    : cn.wwl.orimod.progression.SpiritProgression.energy(player) < SpiritCombat.BOW_COST ? "combat.orimod.no_energy"
                    : "combat.orimod.busy"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player) SpiritCombat.releaseBow(player, stack);
    }
}
