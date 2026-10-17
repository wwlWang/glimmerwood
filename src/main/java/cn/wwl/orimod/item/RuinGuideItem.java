package cn.wwl.orimod.item;

import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.RuinLocator;
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

/** A replaceable inventory entry to the same bounded search offered by the guide panel. */
public final class RuinGuideItem extends Item {
    public RuinGuideItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (SpiritData.isAwakened(player)) RuinLocator.request(serverPlayer);
            else player.displayClientMessage(Component.translatable("progress.orimod.awakening_required"), false);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.orimod.ruin_guide.desc"));
    }
}
