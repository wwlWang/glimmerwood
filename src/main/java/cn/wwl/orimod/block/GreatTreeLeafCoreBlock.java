package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;

/** Solid backing behind the landmark's cutout skin; uses the same bounded bloom queue. */
public final class GreatTreeLeafCoreBlock extends Block {
    public GreatTreeLeafCoreBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(SpiritLeavesBlock.BLOOMED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(SpiritLeavesBlock.BLOOMED);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!old.is(this)&&level instanceof net.minecraft.server.level.ServerLevel server)cn.wwl.orimod.world.RestorationBloom.changed(server,pos);
    }
}
