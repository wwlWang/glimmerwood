package cn.wwl.orimod.item;

import cn.wwl.orimod.OriMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OriItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OriMod.MOD_ID);
    public static final DeferredItem<SpiritCoreItem> SPIRIT_CORE = ITEMS.register("spirit_core", () -> new SpiritCoreItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SpiritLightItem> SPIRIT_LIGHT = ITEMS.register("spirit_light", () -> new SpiritLightItem(new Item.Properties()));
    public static final DeferredItem<SpiritCellItem> LIFE_CELL = ITEMS.register("life_cell", () -> new SpiritCellItem(cn.wwl.orimod.progression.SpiritCells.Kind.LIFE, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SpiritCellItem> ENERGY_CELL = ITEMS.register("energy_cell", () -> new SpiritCellItem(cn.wwl.orimod.progression.SpiritCells.Kind.ENERGY, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<RuinGuideItem> RUIN_GUIDE = ITEMS.register("ruin_guide", () -> new RuinGuideItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SpiritBladeItem> SPIRIT_BLADE = ITEMS.register("spirit_blade", () -> new SpiritBladeItem(new Item.Properties()));
    public static final DeferredItem<SpiritBowItem> SPIRIT_BOW = ITEMS.register("spirit_bow", () ->
            new SpiritBowItem(new Item.Properties().durability(384)));
    private OriItems() {}
}
