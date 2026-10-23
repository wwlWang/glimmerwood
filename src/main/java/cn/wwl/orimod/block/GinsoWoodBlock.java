package cn.wwl.orimod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Wet bark stays world-lit; only the separate crack/moss mask is emissive. */
public final class GinsoWoodBlock extends Block {
    public static final BooleanProperty VEIN=GreatSpiritWoodBlock.VEIN, MOSS=BooleanProperty.create("moss");
    public static final MapCodec<GinsoWoodBlock> CODEC=simpleCodec(GinsoWoodBlock::new);
    public GinsoWoodBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(VEIN,false).setValue(MOSS,false));}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(VEIN,MOSS);}
}
