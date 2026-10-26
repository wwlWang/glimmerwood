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
    public static final DeferredBlock<CrackedRuinBlock> CRACKED_RUIN_WALL = cracked("cracked_ruin_wall", CrackedRuin.Kind.WALL);
    public static final DeferredBlock<CrackedRuinBlock> CRACKED_RUIN_FLOOR = cracked("cracked_ruin_floor", CrackedRuin.Kind.FLOOR);
    private static DeferredBlock<CrackedRuinBlock> cracked(String id, CrackedRuin.Kind kind) {
        return BLOCKS.register(id, () -> new CrackedRuinBlock(kind, BlockBehaviour.Properties.of().strength(-1,1200)
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noLootTable().sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 3)));
    }
    public static final DeferredBlock<SpiritLightVesselBlock> SPIRIT_LIGHT_VESSEL = BLOCKS.register("spirit_light_vessel",
            () -> new SpiritLightVesselBlock(BlockBehaviour.Properties.of().strength(-1,1200).noLootTable().noOcclusion()
                    .lightLevel(s -> 10).sound(SoundType.AMETHYST)));
    public static final DeferredBlock<SpiritPlatformBlock> SPIRIT_PLATFORM = BLOCKS.register("spirit_platform",
            () -> new SpiritPlatformBlock(BlockBehaviour.Properties.of().strength(2).noOcclusion().dynamicShape()
                    .lightLevel(SpiritPlatformBlock::light).sound(SoundType.AMETHYST)
                    .isSuffocating((s,l,p) -> s.getValue(SpiritPlatformBlock.LIT))
                    .isViewBlocking((s,l,p) -> s.getValue(SpiritPlatformBlock.LIT))));
    public static final DeferredBlock<cn.wwl.orimod.world.SpiritWellBlock> GLADES_WELL=BLOCKS.register("glades_well",()->new cn.wwl.orimod.world.SpiritWellBlock(BlockBehaviour.Properties.of().strength(-1,3600000).noLootTable().lightLevel(s->10).sound(SoundType.AMETHYST)));
    public static final DeferredBlock<SanctuaryPartBlock> SANCTUARY_PART=BLOCKS.register("sanctuary_part",()->new SanctuaryPartBlock(BlockBehaviour.Properties.of()
            .strength(-1,3600000).noLootTable().noOcclusion().sound(SoundType.AMETHYST).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
            .isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false)));
    public static final DeferredBlock<Block> BLIGHTED_SOIL=BLOCKS.registerSimpleBlock("blighted_soil",BlockBehaviour.Properties.of().strength(.6f).sound(SoundType.GRAVEL));
    public static final DeferredBlock<GloomThornsBlock> GLOOM_THORNS=BLOCKS.register("gloom_thorns",()->new GloomThornsBlock(BlockBehaviour.Properties.of().strength(-1,1200).noCollission().noOcclusion().sound(SoundType.SWEET_BERRY_BUSH)));
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> WITHERED_SPIRIT_LOG=BLOCKS.register("withered_spirit_log",()->new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.WOOD)));
    public static final DeferredBlock<GloomCoreBlock> GLOOM_CORE=BLOCKS.register("gloom_core",()->new GloomCoreBlock(BlockBehaviour.Properties.of().strength(-1,3600000).noLootTable().noOcclusion().lightLevel(s->6).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).sound(SoundType.AMETHYST)));
    /** R100: original luminous spirit wood, permanent foliage and a per-player blessing heart. */
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> SPIRIT_LOG = BLOCKS.register("spirit_log",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .strength(2).lightLevel(s -> 4).sound(SoundType.WOOD)));
    public static final DeferredBlock<GreatSpiritWoodBlock> GREAT_SPIRIT_WOOD=BLOCKS.register("great_spirit_wood",()->new GreatSpiritWoodBlock(BlockBehaviour.Properties.of()
            .strength(2).lightLevel(s->s.getValue(GreatSpiritWoodBlock.VEIN)?10:0).sound(SoundType.WOOD)));
    public static final DeferredBlock<GinsoWoodBlock> GINSO_WOOD=BLOCKS.register("ginso_wood",()->new GinsoWoodBlock(BlockBehaviour.Properties.of()
            .strength(2).lightLevel(s->s.getValue(GinsoWoodBlock.VEIN)?8:0).sound(SoundType.WOOD)));
    public static final DeferredBlock<GinsoWaterHeartBlock> GINSO_WATER_HEART=BLOCKS.register("ginso_water_heart",()->new GinsoWaterHeartBlock(BlockBehaviour.Properties.of()
            .strength(-1,3600000).noLootTable().noOcclusion().lightLevel(s->12).emissiveRendering((s,l,p)->true).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredBlock<GinsoRootLipBlock> GINSO_ROOT_LIP=BLOCKS.register("ginso_root_lip",()->new GinsoRootLipBlock(BlockBehaviour.Properties.of()
            .strength(2).noLootTable().noOcclusion().sound(SoundType.WOOD)));
    public static final DeferredBlock<GinsoLandingBlock> GINSO_LANDING=BLOCKS.register("ginso_landing",()->new GinsoLandingBlock(BlockBehaviour.Properties.of()
            .strength(2).sound(SoundType.MOSS)));
    public static final DeferredBlock<GinsoFungusBlock> GINSO_FUNGUS=BLOCKS.register("ginso_fungus",()->new GinsoFungusBlock(BlockBehaviour.Properties.of()
            .instabreak().noCollission().noOcclusion().noLootTable().lightLevel(s->8).sound(SoundType.FUNGUS)));
    public static final DeferredBlock<Block> GINSO_LEAVES=BLOCKS.registerSimpleBlock("ginso_leaves",BlockBehaviour.Properties.of()
            .strength(.2f).noOcclusion().sound(SoundType.AZALEA_LEAVES).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
    public static final DeferredBlock<GinsoCanopyBlock> GINSO_CANOPY=BLOCKS.register("ginso_canopy",()->new GinsoCanopyBlock(BlockBehaviour.Properties.of()
            .strength(.2f).noOcclusion().sound(SoundType.AZALEA_LEAVES).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false)));
    public static final DeferredBlock<Block> GINSO_LEAF_CORE=BLOCKS.registerSimpleBlock("ginso_leaf_core",BlockBehaviour.Properties.of()
            .strength(.2f).sound(SoundType.AZALEA_LEAVES).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
    public static final DeferredBlock<SpiritVineBlock> GINSO_VINE=BLOCKS.register("ginso_vine",()->new SpiritVineBlock(BlockBehaviour.Properties.of()
            .instabreak().noCollission().noOcclusion().lightLevel(s->4).sound(SoundType.CAVE_VINES)));
    public static final DeferredBlock<GreatSpiritRootBlock> GREAT_SPIRIT_ROOT=BLOCKS.register("great_spirit_root",()->new GreatSpiritRootBlock(BlockBehaviour.Properties.of()
            .strength(2).lightLevel(s->s.getValue(GreatSpiritRootBlock.LIGHT).emission).sound(SoundType.WOOD)));
    public static final DeferredBlock<SpiritLeavesBlock> SPIRIT_LEAVES = BLOCKS.register("spirit_leaves", () -> new SpiritLeavesBlock(BlockBehaviour.Properties.of()
            .strength(.2f).noOcclusion().lightLevel(s -> s.getValue(SpiritLeavesBlock.BLOOMED) ? 12 : 9).sound(SoundType.AZALEA_LEAVES)
            .emissiveRendering((s, l, p) -> true)
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<GreatTreeLeafCoreBlock> GREAT_TREE_LEAF_CORE=BLOCKS.register("great_tree_leaf_core",()->new GreatTreeLeafCoreBlock(BlockBehaviour.Properties.of()
            .strength(.2f).noLootTable().sound(SoundType.AZALEA_LEAVES).lightLevel(s->s.getValue(SpiritLeavesBlock.BLOOMED)?12:9)
            .emissiveRendering((s,l,p)->true).isSuffocating((s,l,p)->false)));
    public static final DeferredBlock<SpiritVineBlock> SPIRIT_VINE = BLOCKS.register("spirit_vine",
            () -> new SpiritVineBlock(BlockBehaviour.Properties.of().instabreak().noCollission().noOcclusion()
                    .lightLevel(s -> 10).sound(SoundType.CAVE_VINES).emissiveRendering((s, l, p) -> true)));
    public static final DeferredBlock<SpiritTreeHeartBlock> SPIRIT_TREE_HEART = BLOCKS.register("spirit_tree_heart",
            () -> new SpiritTreeHeartBlock(BlockBehaviour.Properties.of().strength(-1, 3600000).noLootTable()
                    .lightLevel(s -> 15).sound(SoundType.AMETHYST).emissiveRendering((s, l, p) -> true)));
    public static final DeferredBlock<GreatTreeHeartBlock> GREAT_TREE_HEART=BLOCKS.register("great_tree_heart",()->new GreatTreeHeartBlock(BlockBehaviour.Properties.of()
            .strength(-1,3600000).noLootTable().noOcclusion().lightLevel(s->15).sound(SoundType.AMETHYST)
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    /** R96: keep the original registry id so existing ruins and player-built Bash routes remain valid. */
    public static final DeferredBlock<SpiritLanternBlock> BASH_ANCHOR = BLOCKS.register("bash_anchor", () -> new SpiritLanternBlock(BlockBehaviour.Properties.of()
            .strength(1.5f).noCollission().noOcclusion().lightLevel(state -> SpiritLanternBlock.lightLevel(state.getValue(SpiritLanternBlock.LIT)))
            .sound(SoundType.AMETHYST)));
    public static final DeferredBlock<Block> GRAPPLE_FLOWER = BLOCKS.registerSimpleBlock("grapple_flower", BlockBehaviour.Properties.of()
            .strength(1.5f).noCollission().noOcclusion().lightLevel(state -> 12).sound(SoundType.AZALEA_LEAVES));
    /** R40: glowing updraft vent that lifts gliding players. */
    public static final DeferredBlock<UpdraftBlock> UPDRAFT = BLOCKS.register("updraft", () -> new UpdraftBlock(BlockBehaviour.Properties.of()
            .strength(1.5f).noOcclusion().lightLevel(state -> 8).sound(SoundType.AMETHYST)));
    public static final DeferredBlock<GreatTreeWindBlock> GREAT_TREE_WIND=BLOCKS.register("great_tree_wind",()->new GreatTreeWindBlock(BlockBehaviour.Properties.of()
            .strength(-1,3600000).noLootTable().noCollission().noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredBlock<GreatTreeLandingBlock> GREAT_TREE_LANDING=BLOCKS.register("great_tree_landing",()->new GreatTreeLandingBlock(BlockBehaviour.Properties.of()
            .strength(-1,3600000).noLootTable().noOcclusion().lightLevel(s->12).sound(SoundType.AMETHYST)
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
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
    /** R70: storm altar (Gloom Matriarch) in the forest spring ruin. */
    public static final DeferredBlock<StormAltarBlock> STORM_ALTAR = BLOCKS.register("storm_altar", () -> new StormAltarBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noLootTable().lightLevel(state -> 9).sound(SoundType.LODESTONE)));
    /** R57: keystone doors and keystone pedestals inside ruins. */
    public static final DeferredBlock<KeystoneDoorBlock> KEYSTONE_DOOR = BLOCKS.register("keystone_door", () -> new KeystoneDoorBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noLootTable().lightLevel(state -> 3 + 6 * state.getValue(KeystoneDoorBlock.FILLED)).sound(SoundType.TUFF)));
    public static final DeferredBlock<KeystonePedestalBlock> KEYSTONE_PEDESTAL = BLOCKS.register("keystone_pedestal", () -> new KeystonePedestalBlock(
            BlockBehaviour.Properties.of().strength(3, 12).noOcclusion().lightLevel(state -> state.getValue(KeystonePedestalBlock.FULL) ? 10 : 2).sound(SoundType.AMETHYST)));
    /** R61: gloom water hazard (cleansed by Charge Flame, Light Burst or the Gloom Heart's defeat). */
    public static final DeferredBlock<GloomWaterBlock> GLOOM_WATER = BLOCKS.register("gloom_water", () -> new GloomWaterBlock(
            BlockBehaviour.Properties.of().strength(-1, 100).noLootTable().noCollission().noOcclusion().replaceable()
                    .liquid().lightLevel(state -> 4).sound(SoundType.EMPTY)));
    /** R64: burrowable spirit sand, placed only in ruins. */
    public static final DeferredBlock<SpiritSandBlock> SPIRIT_SAND = BLOCKS.register("spirit_sand", () -> new SpiritSandBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noLootTable().noOcclusion().dynamicShape().sound(SoundType.SAND)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).lightLevel(state -> 3)));
    /** R62: map stone at each ruin entrance. */
    public static final DeferredBlock<MapStoneBlock> MAP_STONE = BLOCKS.register("map_stone", () -> new MapStoneBlock(
            BlockBehaviour.Properties.of().strength(-1, 3600000).noLootTable().lightLevel(state -> 8).sound(SoundType.LODESTONE)));
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
        OriItems.ITEMS.register("keystone_door", () -> new cn.wwl.orimod.item.KeystoneDoorItem(KEYSTONE_DOOR.get(), new net.minecraft.world.item.Item.Properties()));
        // R83: every placeable ruin block gets an item so builders and testers can use it from the creative tab.
        for(var block:java.util.List.of(GLADES_WELL,FOREST_SHRINE,CREEK_SHRINE,CAVE_SHRINE,MAP_STONE,GREAT_TREE_HEART,GLOOM_ALTAR,STORM_ALTAR))
            OriItems.ITEMS.register(block.getId().getPath(),()->new cn.wwl.orimod.item.SanctuaryItem(block.get(),new net.minecraft.world.item.Item.Properties()));
        for (var block : java.util.List.of(LIFE_CELL_PEDESTAL, ENERGY_CELL_PEDESTAL, KEYSTONE_PEDESTAL, SPIRIT_SAND,
                BLIGHTED_SOIL, GLOOM_THORNS, WITHERED_SPIRIT_LOG, GLOOM_CORE, GLOOM_WATER,
                SPIRIT_LOG, GINSO_WATER_HEART, GINSO_WOOD, GINSO_ROOT_LIP, GINSO_LANDING, GINSO_FUNGUS, GINSO_LEAVES, GINSO_CANOPY, GINSO_LEAF_CORE, GINSO_VINE, GREAT_SPIRIT_WOOD, GREAT_SPIRIT_ROOT, SPIRIT_LEAVES, GREAT_TREE_LEAF_CORE, SPIRIT_VINE, SPIRIT_TREE_HEART, CRACKED_RUIN_WALL, CRACKED_RUIN_FLOOR, SPIRIT_LIGHT_VESSEL, SPIRIT_PLATFORM))
            OriItems.ITEMS.registerSimpleBlockItem(block);
        BLOCKS.register(bus);
    }
}
