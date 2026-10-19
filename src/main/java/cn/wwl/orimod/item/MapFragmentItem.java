package cn.wwl.orimod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * R68 map fragment (after BF's map fragments): the first activation of each map stone and every Gloom Hawk drop one.
 * Reading a fragment consumes it, grants a little Spirit Light and points the ruin guide at the nearest ruin.
 */
public final class MapFragmentItem extends Item {
    public static final int LIGHT = 5;

    public MapFragmentItem(Properties properties) { super(properties); }

    /** @return whether the fragment was read (the player must be awakened). */
    public static boolean read(ServerPlayer player, ItemStack stack) {
        if (!cn.wwl.orimod.spirit.SpiritData.isAwakened(player)) { player.displayClientMessage(Component.translatable("message.orimod.map_fragment.dormant"), true); return false; }
        stack.consume(1, player);
        cn.wwl.orimod.progression.SpiritProgression.grantLight(player, LIGHT);
        cn.wwl.orimod.world.RuinLocator.request(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 1.1f);
        player.displayClientMessage(Component.translatable("message.orimod.map_fragment.read", LIGHT), false);
        return true;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        return player instanceof ServerPlayer server && read(server, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.orimod.map_fragment").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
