package cn.wwl.orimod.item;

import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.progression.SpiritCells;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** R36: Life Cell / Energy Cell; use to absorb permanently (see {@link SpiritCells}). */
public final class SpiritCellItem extends Item {
    private final SpiritCells.Kind kind;
    public SpiritCellItem(SpiritCells.Kind kind, Properties properties) { super(properties); this.kind = kind; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!SpiritCells.absorb(serverPlayer, kind)) {
                player.displayClientMessage(Component.translatable(cn.wwl.orimod.spirit.SpiritData.isAwakened(player)
                        ? "message.orimod.cell_full" : "message.orimod.cell_dormant"), true);
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
            SpiritFx.emit(serverPlayer.serverLevel(), SpiritFxKind.FORM_SWITCH, player.position(), player);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, kind == SpiritCells.Kind.LIFE ? 1.1f : 1.5f);
            player.displayClientMessage(Component.translatable("message.orimod.cell_absorbed." + kind.name().toLowerCase(java.util.Locale.ROOT),
                    SpiritCells.count(player, kind), SpiritCells.MAX), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.orimod." + kind.name().toLowerCase(java.util.Locale.ROOT) + "_cell").withStyle(ChatFormatting.AQUA));
    }
}
