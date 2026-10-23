package cn.wwl.orimod.block;

import cn.wwl.orimod.progression.Restoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

/** The great heart grants no skill. Three wisps alone admit an awakened spirit to the ritual. */
public final class GreatTreeHeartBlock extends SanctuaryBlock {
    public GreatTreeHeartBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(SpiritTreeHeartBlock.BLOOMED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(SpiritTreeHeartBlock.BLOOMED);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer p)Restoration.start(p,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!old.is(this)&&level instanceof ServerLevel server){
            cn.wwl.orimod.world.GloomTideDefense.invalidate(server,pos);
            cn.wwl.orimod.world.RestorationBloom.changed(server,pos);
            PlatformLight.push(server,net.minecraft.world.phys.Vec3.atCenterOf(pos),PlatformRules.Source.TREE);
        }
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        super.onRemove(state,level,pos,next,moving);
        if(!next.is(this)&&level instanceof ServerLevel server){cn.wwl.orimod.world.GloomTideDefense.invalidate(server,pos);PlatformLight.lost(server,pos,PlatformRules.Source.TREE);}
    }
}
