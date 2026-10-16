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
    private OriBlocks() {}
    public static void register(IEventBus bus) {
        OriItems.ITEMS.registerSimpleBlockItem(BASH_ANCHOR);
        OriItems.ITEMS.registerSimpleBlockItem(GRAPPLE_FLOWER);
        BLOCKS.register(bus);
    }
}
