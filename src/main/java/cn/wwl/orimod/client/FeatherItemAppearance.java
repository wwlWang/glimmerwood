package cn.wwl.orimod.client;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
public final class FeatherItemAppearance {
    private FeatherItemAppearance(){}
    public static boolean spirit(){var p=Minecraft.getInstance().player;return p!=null && SpiritData.isSpirit(p);}
    public static void register(){net.minecraft.client.renderer.item.ItemProperties.register(Items.ELYTRA,
            ResourceLocation.parse("orimod:spirit_form"),(stack,level,entity,seed)->spirit()?1:0);}
}
