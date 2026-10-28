package cn.wwl.orimod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Position-preserving, per-block filter. Ring foliage must not change supporting
 * terrain or a foreign structure, either before grading or on repeat placement. */
public final class CreekRimDecorationProcessor extends StructureProcessor {
    public static final CreekRimDecorationProcessor INSTANCE=new CreekRimDecorationProcessor();
    public static final MapCodec<CreekRimDecorationProcessor> CODEC=MapCodec.unit(()->INSTANCE);
    private CreekRimDecorationProcessor(){}
    public static boolean mayReplace(BlockState existing){
        return existing.isAir()||existing.canBeReplaced()&&existing.getFluidState().isEmpty();
    }
    @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level,BlockPos offset,BlockPos pivot,
            StructureTemplate.StructureBlockInfo raw,StructureTemplate.StructureBlockInfo placed,StructurePlaceSettings settings){
        if(!CreekRuinTerrain.rimColumn(raw.pos().getX(),raw.pos().getZ()))return placed;
        return mayReplace(level.getBlockState(placed.pos()))?placed:null;
    }
    @Override protected StructureProcessorType<?> getType(){return OriWorldgen.CREEK_RIM_DECORATION.get();}
}
