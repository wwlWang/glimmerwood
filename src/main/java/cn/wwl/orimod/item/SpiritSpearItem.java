package cn.wwl.orimod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** R60: throws a Spirit Spear on use (needs the Spirit Spear ability and energy; the item is never consumed). */
public final class SpiritSpearItem extends Item {
    public SpiritSpearItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        cn.wwl.orimod.combat.Flash.stop((ServerPlayer) player);
        var spear = cn.wwl.orimod.combat.SpiritSpear.thrown((ServerPlayer) player);
        if (spear == null) return InteractionResultHolder.fail(stack);
        player.getCooldowns().addCooldown(this, cn.wwl.orimod.combat.SpiritSpear.COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SpiritTooltips.add(lines, "tooltip.orimod.spear.flavor", "tooltip.orimod.spear", "tooltip.orimod.spirit_required");
    }
    @Override public Component getName(ItemStack stack) { return SpiritTooltips.name(super.getName(stack)); }
}
