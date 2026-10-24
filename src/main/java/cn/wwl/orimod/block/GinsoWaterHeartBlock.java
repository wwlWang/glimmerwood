package cn.wwl.orimod.block;

import cn.wwl.orimod.world.GinsoEscape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Always-present, shared crystal. Taking its light starts only the clicking player's escape. */
public final class GinsoWaterHeartBlock extends SanctuaryBlock {
    public GinsoWaterHeartBlock(Properties p){super(p);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(3,0,3,13,22,13);}
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer p)GinsoEscape.start(p,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
