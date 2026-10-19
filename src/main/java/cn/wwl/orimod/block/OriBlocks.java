package cn.wwl.orimod.block;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.item.OriItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OriBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OriMod.MOD_ID);
    public static final DeferredBlock<Block> BASH_ANCHOR = BLOCKS.registerSimpleBlock("bash_anchor", BlockBehaviour.Properties.of()
            .strength(1.5f).noCollission().noOcclusion().lightLevel(state -> 12).sound(SoundType.AMETHYST));
    public static final DeferredBlock<Block> GRAPPLE_FLOWER = BLOCKS.registerSimpleBlock("grapple_flower", BlockBehaviour.Properties.of()
            .strength(1.5f).noCollission().noOcclusion().lightLevel(state -> 12).sound(SoundType.AZALEA_LEAVES));
    /** R40: glowing updraft vent that lifts gliding players. */
    public static final DeferredBlock<UpdraftBlock> UPDRAFT = BLOCKS.register("updraft", () -> new UpdraftBlock(BlockBehaviour.Properties.of()
            .strength(1.5f).noOcclusion().lightLevel(state -> 8).sound(SoundType.AMETHYST)));
    /** R44: Life / Energy Cell pedestals placed inside ruins. */
    public static final DeferredBlock<CellPedestalBlock> LIFE_CELL_PEDESTAL = pedestal(cn.wwl.orimod.progression.SpiritCells.Kind.LIFE);
    public static final DeferredBlock<CellPedestalBlock> ENERGY_CELL_PEDESTAL = pedestal(cn.wwl.orimod.progression.SpiritCells.Kind.ENERGY);
    private static DeferredBlock<CellPedestalBlock> pedestal(cn.wwl.orimod.progression.SpiritCells.Kind kind) {
        return BLOCKS.register(kind.name().toLowerCase(java.util.Locale.ROOT) + "_cell_pedestal", () -> new CellPedestalBlock(kind,
                BlockBehaviour.Properties.of().strength(3, 12).noOcclusion().lightLevel(state -> 11).sound(SoundType.AMETHYST)));
    }
    /** R50: gloom altar that wakes the Gloom Heart boss. */
    public static final DeferredBlock<GloomAltarBlock> GLOOM_ALTAR = BLOCKS.register("gloom_altar", () -> new GloomAltarBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noLootTable().lightLevel(state -> 7).sound(SoundType.SCULK_CATALYST)));
    /** R57: keystone doors and keystone pedestals inside ruins. */
    public static final DeferredBlock<KeystoneDoorBlock> KEYSTONE_DOOR = BLOCKS.register("keystone_door", () -> new KeystoneDoorBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noLootTable().lightLevel(state -> 3 + 6 * state.getValue(KeystoneDoorBlock.FILLED)).sound(SoundType.TUFF)));
    public static final DeferredBlock<KeystonePedestalBlock> KEYSTONE_PEDESTAL = BLOCKS.register("keystone_pedestal", () -> new KeystonePedestalBlock(
            BlockBehaviour.Properties.of().strength(3, 12).noOcclusion().lightLevel(state -> state.getValue(KeystonePedestalBlock.FULL) ? 10 : 2).sound(SoundType.AMETHYST)));
    public static final DeferredBlock<cn.wwl.orimod.world.RuinShrineBlock> FOREST_SHRINE = shrine(cn.wwl.orimod.world.RuinKind.FOREST);
    public static final DeferredBlock<cn.wwl.orimod.world.RuinShrineBlock> CREEK_SHRINE = shrine(cn.wwl.orimod.world.RuinKind.CREEK);
    public static final DeferredBlock<cn.wwl.orimod.world.RuinShrineBlock> CAVE_SHRINE = shrine(cn.wwl.orimod.world.RuinKind.CAVE);
    private static DeferredBlock<cn.wwl.orimod.world.RuinShrineBlock> shrine(cn.wwl.orimod.world.RuinKind kind) {
        return BLOCKS.register(kind.id + "_shrine", () -> new cn.wwl.orimod.world.RuinShrineBlock(kind,
                BlockBehaviour.Properties.of().strength(3, 12).lightLevel(state -> 10).sound(SoundType.AMETHYST)));
    }
    private OriBlocks() {}
    public static void register(IEventBus bus) {
        OriItems.ITEMS.registerSimpleBlockItem(BASH_ANCHOR);
        OriItems.ITEMS.registerSimpleBlockItem(GRAPPLE_FLOWER);
        OriItems.ITEMS.registerSimpleBlockItem(UPDRAFT);
        BLOCKS.register(bus);
    }
}
