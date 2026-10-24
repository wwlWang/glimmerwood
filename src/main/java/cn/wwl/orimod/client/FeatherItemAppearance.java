package cn.wwl.orimod.client;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
public final class FeatherItemAppearance {
    private FeatherItemAppearance(){}
    public static boolean spirit(){var p=Minecraft.getInstance().player;return p!=null && SpiritData.isSpirit(p);}
    public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        if (!event.getItemStack().is(Items.ELYTRA)) return;
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.orimod.feather.spirit")
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.orimod.feather.human")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    public static void register(){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FeatherItemAppearance::tooltip);
        net.minecraft.client.renderer.item.ItemProperties.register(Items.ELYTRA,
            ResourceLocation.parse("orimod:spirit_form"),(stack,level,entity,seed)->spirit()?1:0);}
}
