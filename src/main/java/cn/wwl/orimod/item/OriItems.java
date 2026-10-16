package cn.wwl.orimod.item;

import cn.wwl.orimod.OriMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OriItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OriMod.MOD_ID);
    public static final DeferredItem<SpiritCoreItem> SPIRIT_CORE = ITEMS.register("spirit_core", () -> new SpiritCoreItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SPIRIT_LIGHT = ITEMS.registerSimpleItem("spirit_light");
    public static final DeferredItem<SpiritBladeItem> SPIRIT_BLADE = ITEMS.register("spirit_blade", () -> new SpiritBladeItem(new Item.Properties()));
    public static final DeferredItem<SpiritBowItem> SPIRIT_BOW = ITEMS.register("spirit_bow", () ->
            new SpiritBowItem(new Item.Properties().durability(384)));
    private OriItems() {}
}
