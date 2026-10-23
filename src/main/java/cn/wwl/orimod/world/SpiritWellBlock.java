package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** Shared R41 well facing and travel identity. Ruin shrines override interaction to retain their challenge panel. */
public class SpiritWellBlock extends cn.wwl.orimod.block.SanctuaryBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public SpiritWellBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level level,BlockPos pos,Player p,BlockHitResult hit){
        if(p instanceof ServerPlayer player && SpiritData.isAwakened(player)){
            SpiritProgression.reward(player,"discover/"+ChallengeSession.key(level.dimension(),pos),0,0);
            SpiritWell.refill(player); GinsoCourse.checkpoint(player,pos); SpiritWell.open(player,pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onPlace(BlockState s,Level level,BlockPos p,BlockState old,boolean moving){super.onPlace(s,level,p,old,moving);if(level instanceof ServerLevel server && !old.is(this))GloomTideDefense.invalidate(server,p);}
    @Override protected void onRemove(BlockState s,Level level,BlockPos p,BlockState next,boolean moving){super.onRemove(s,level,p,next,moving);if(level instanceof ServerLevel server && !next.is(this))GloomTideDefense.invalidate(server,p);}
}
