package cn.wwl.orimod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Per-invocation new-forest filter, including explicit template AIR. */
public final class ForestTerrainProcessor extends StructureProcessor {
    public static final MapCodec<ForestTerrainProcessor> CODEC=MapCodec.unit(()->new ForestTerrainProcessor(null,ForestFluidProtection.FOREIGN_ONLY));
    private final ForestTerrainContract contract;
    private final ForestFluidProtection fluids;
    ForestTerrainProcessor(ForestTerrainContract contract,ForestFluidProtection fluids){this.contract=contract;this.fluids=fluids;}
    @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level,BlockPos offset,BlockPos pivot,
            StructureTemplate.StructureBlockInfo raw,StructureTemplate.StructureBlockInfo placed,StructurePlaceSettings settings){
        var old=level.getBlockState(placed.pos());
        if(!fluids.allows(level,placed.pos(),placed.state()))return null;
        if(!CreekTerrainSurface.mutable(old,level,placed.pos())||level.getBlockEntity(placed.pos())!=null)return null;
        // Authored inside cells take precedence over vegetation. Outside LAND,
        // decorations may only replace soft, non-solid vegetation/air.
        if(contract!=null&&!contract.land(raw.pos())&&!CreekTerrainSurface.replaceable(old,level,placed.pos()))return null;
        return placed;
    }
    @Override protected StructureProcessorType<?> getType(){return OriWorldgen.FOREST_TERRAIN_PROTECTION.get();}
}
