package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Legacy single core or item-placed architecture. Only the core owns rendering/logic. */
public class SanctuaryBlock extends ProtectedBlock implements EntityBlock {
    public static final BooleanProperty ASSEMBLED=BooleanProperty.create("assembled");
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public SanctuaryBlock(Properties properties) {
        super(properties.noOcclusion());
        if(SanctuaryKind.supports(this))registerDefaultState(stateDefinition.any().setValue(ASSEMBLED,false).setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){if(SanctuaryKind.supports(this))b.add(ASSEMBLED,FACING);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SanctuaryAssembly.assembled(s)?SanctuaryAssembly.shape(s).outline():super.getShape(s,l,p,c);}
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SanctuaryAssembly.assembled(s)?SanctuaryAssembly.shape(s).collision():super.getCollisionShape(s,l,p,c);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){super.onRemove(s,l,p,next,moving);SanctuaryAssembly.removed(s,l,p,next);}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.hasProperty(FACING)?s.setValue(FACING,r.rotate(s.getValue(FACING))):s;}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.hasProperty(FACING)?s.setValue(FACING,m.mirror(s.getValue(FACING))):s;}
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SanctuaryBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != OriBlockEntities.SANCTUARY.get()
                || !(this instanceof GloomAltarBlock || this instanceof StormAltarBlock)) return null;
        return (l,p,s,b) -> ((SanctuaryBlockEntity)b).tickAltar();
    }
}
