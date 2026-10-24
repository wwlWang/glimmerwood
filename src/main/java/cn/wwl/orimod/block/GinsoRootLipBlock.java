package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A mossy root lip, with the same unjumpable 1.5-block barrier as course fences.
 * This separate block is used only by the new escape overlay; saved courses stay unchanged. */
public final class GinsoRootLipBlock extends Block {
    private static final VoxelShape BARRIER=Block.box(0,0,0,16,24,16);
    public GinsoRootLipBlock(Properties properties){super(properties);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return BARRIER;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return BARRIER;}
}
