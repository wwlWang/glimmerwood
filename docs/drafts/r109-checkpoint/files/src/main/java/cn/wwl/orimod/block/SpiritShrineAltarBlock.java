package cn.wwl.orimod.block;

import cn.wwl.orimod.world.ShrineTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SpiritShrineAltarBlock extends Block {
    public static final BooleanProperty COMPLETED=BooleanProperty.create("completed");
    public SpiritShrineAltarBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(COMPLETED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(COMPLETED);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos pos,Player p,BlockHitResult hit){
        if(p instanceof ServerPlayer player)ShrineTrials.get(player.serverLevel()).start(player,pos);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
