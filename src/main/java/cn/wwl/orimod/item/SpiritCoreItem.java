package cn.wwl.orimod.item;

import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SpiritCoreItem extends Item {
    public SpiritCoreItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!SpiritData.isAwakened(player)) {
                player.setData(SpiritData.AWAKENED, true);
                cn.wwl.orimod.progression.SpiritProgression.grantGuide(serverPlayer);
                player.sendSystemMessage(Component.translatable("message.orimod.awakened"));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new cn.wwl.orimod.network.ProgressReplyPayload(Component.translatable("message.orimod.awakened"), true));
            }
            SpiritForm.toggle(serverPlayer);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
