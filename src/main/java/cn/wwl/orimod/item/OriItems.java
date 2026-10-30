package cn.wwl.orimod.item;

import cn.wwl.orimod.OriMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OriItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OriMod.MOD_ID);
    public static final DeferredItem<Item> GLOOM_RESIDUE=ITEMS.registerSimpleItem("gloom_residue",new Item.Properties());
    public static final DeferredItem<SpiritCoreItem> SPIRIT_CORE = ITEMS.register("spirit_core", () -> new SpiritCoreItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SpiritLightItem> SPIRIT_LIGHT = ITEMS.register("spirit_light", () -> new SpiritLightItem(new Item.Properties()));
    public static final DeferredItem<SpiritCellItem> LIFE_CELL = ITEMS.register("life_cell", () -> new SpiritCellItem(cn.wwl.orimod.progression.SpiritCells.Kind.LIFE, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SpiritCellItem> ENERGY_CELL = ITEMS.register("energy_cell", () -> new SpiritCellItem(cn.wwl.orimod.progression.SpiritCells.Kind.ENERGY, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<RuinGuideItem> RUIN_GUIDE = ITEMS.register("ruin_guide", () -> new RuinGuideItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SpiritBladeItem> SPIRIT_BLADE = ITEMS.register("spirit_blade", () -> new SpiritBladeItem(new Item.Properties()));
    public static final DeferredItem<SpiritBowItem> SPIRIT_BOW = ITEMS.register("spirit_bow", () ->
            new SpiritBowItem(new Item.Properties().durability(384)));
    /** R49: Spirit Smash heavy weapon. */
    public static final DeferredItem<SpiritHammerItem> SPIRIT_HAMMER = ITEMS.register("spirit_hammer", () ->
            new SpiritHammerItem(new Item.Properties().stacksTo(1)));
    /** R57: keystones open keystone doors in ruins. */
    public static final DeferredItem<MapFragmentItem> MAP_FRAGMENT = ITEMS.register("map_fragment", () -> new MapFragmentItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> KEYSTONE = ITEMS.register("keystone", () -> new KeystoneItem(new Item.Properties().stacksTo(16)));
    /** R60: Spirit Spear. */
    public static final DeferredItem<SpiritSpearItem> SPIRIT_SPEAR = ITEMS.register("spirit_spear", () -> new SpiritSpearItem(new Item.Properties().stacksTo(1)));
    /** R83: spawn eggs for the original enemies and bosses (vanilla template egg, our colours). */
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> GLOOM_SPITTER_SPAWN_EGG = egg("gloom_spitter", cn.wwl.orimod.entity.OriMobs.GLOOM_SPITTER, 0x2A1238, 0xB060FF);
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> GLOOM_BEETLE_SPAWN_EGG = egg("gloom_beetle", cn.wwl.orimod.entity.OriMobs.GLOOM_BEETLE, 0x292039, 0xA65CE0);
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> GLOOM_HAWK_SPAWN_EGG = egg("gloom_hawk", cn.wwl.orimod.entity.OriMobs.GLOOM_HAWK, 0x1C1430, 0x8A3AD8);
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> GLOOM_HEART_SPAWN_EGG = egg("gloom_heart", cn.wwl.orimod.entity.OriMobs.GLOOM_HEART, 0x3A0A2C, 0xFF3A8A);
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> GLOOM_MATRIARCH_SPAWN_EGG = egg("gloom_matriarch", cn.wwl.orimod.entity.OriMobs.GLOOM_MATRIARCH, 0x141A3A, 0x5A7BD8);
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> PRACTICE_TARGET_SPAWN_EGG = ITEMS.register("practice_target_spawn_egg",()->new PracticeTargetEggItem(new Item.Properties()));

    private static <T extends net.minecraft.world.entity.Mob> DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> egg(String name,
            java.util.function.Supplier<net.minecraft.world.entity.EntityType<T>> type, int base, int spots) {
        return ITEMS.register(name + "_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(type, base, spots, new Item.Properties()));
    }

    /** R83: the OriMod creative tab lists every item this mod registers, in registration order. */
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, OriMod.MOD_ID);
    public static final java.util.function.Supplier<net.minecraft.world.item.CreativeModeTab> TAB = TABS.register("orimod", () -> net.minecraft.world.item.CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.orimod"))
            .icon(() -> new net.minecraft.world.item.ItemStack(SPIRIT_CORE.get()))
            .displayItems((parameters, output) -> { for (var entry : ITEMS.getEntries()) output.accept(entry.get()); })
            .build());

    public static void register(net.neoforged.bus.api.IEventBus bus) { ITEMS.register(bus); TABS.register(bus); }

    private OriItems() {}
}
