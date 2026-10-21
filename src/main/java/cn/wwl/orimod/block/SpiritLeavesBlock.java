package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class SpiritLeavesBlock extends Block {
    public static final BooleanProperty BLOOMED=SpiritTreeHeartBlock.BLOOMED;
    public SpiritLeavesBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(BLOOMED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BLOOMED);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!old.is(this) && level instanceof net.minecraft.server.level.ServerLevel server)cn.wwl.orimod.world.RestorationBloom.changed(server,pos);
    }
}
