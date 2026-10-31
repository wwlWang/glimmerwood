package cn.wwl.orimod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Bark on every face, including root tops. A separate emissive model layer lights only the vein pixels. */
public final class GreatSpiritWoodBlock extends Block {
    public static final BooleanProperty VEIN=BooleanProperty.create("vein");
    public static final MapCodec<GreatSpiritWoodBlock> CODEC=simpleCodec(GreatSpiritWoodBlock::new);
    public GreatSpiritWoodBlock(Properties properties){super(ProtectedBlock.durable(properties));registerDefaultState(stateDefinition.any().setValue(VEIN,false));}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(VEIN);}
}
