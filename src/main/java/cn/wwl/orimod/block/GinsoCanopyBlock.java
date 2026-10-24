package cn.wwl.orimod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** v3-only foliage. Keep the legacy ginso_leaves state and rendering untouched. */
public final class GinsoCanopyBlock extends Block {
    public GinsoCanopyBlock(Properties properties){super(properties);}
    @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction face){
        return adjacent.is(this)||adjacent.is(OriBlocks.GINSO_LEAF_CORE)||super.skipRendering(state,adjacent,face);
    }
}
