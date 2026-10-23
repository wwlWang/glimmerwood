package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Decorative architecture around the original interactive/collidable block. No extra world blocks. */
public class SanctuaryBlock extends Block implements EntityBlock {
    public SanctuaryBlock(Properties properties) { super(properties.noOcclusion()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SanctuaryBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != OriBlockEntities.SANCTUARY.get()
                || !(this instanceof GloomAltarBlock || this instanceof StormAltarBlock)) return null;
        return (l,p,s,b) -> ((SanctuaryBlockEntity)b).tickAltar();
    }
}
