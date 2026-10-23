package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class SpiritLeavesBlock extends Block {
    public static final BooleanProperty BLOOMED=SpiritTreeHeartBlock.BLOOMED;
    // Opt in only the enlarged landmark. Existing/Ancestral leaves keep their original cutout rendering.
    public static final BooleanProperty GREAT_TREE=BooleanProperty.create("great_tree");
    public SpiritLeavesBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(BLOOMED,false).setValue(GREAT_TREE,false));}
    @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction face){
        return state.getValue(GREAT_TREE)&&(adjacent.is(this)&&adjacent.getValue(GREAT_TREE)||adjacent.is(OriBlocks.GREAT_TREE_LEAF_CORE))
                ||super.skipRendering(state,adjacent,face);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BLOOMED,GREAT_TREE);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!old.is(this) && level instanceof net.minecraft.server.level.ServerLevel server)cn.wwl.orimod.world.RestorationBloom.changed(server,pos);
    }
}
