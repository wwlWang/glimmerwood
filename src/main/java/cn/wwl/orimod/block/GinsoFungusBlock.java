package cn.wwl.orimod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Decorative thin luminous shelf, never an obstacle in the future ascent/escape. */
public final class GinsoFungusBlock extends Block {
    public static final MapCodec<GinsoFungusBlock> CODEC=simpleCodec(GinsoFungusBlock::new);
    private static final VoxelShape OUTLINE=Block.box(1,4,1,15,8,15);
    public GinsoFungusBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return OUTLINE;}
}
