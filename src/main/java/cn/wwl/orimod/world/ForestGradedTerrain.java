package cn.wwl.orimod.world;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Opt-in ForestTerrainVersion=1; the old ForestRuinTerrain remains frozen. */
final class ForestGradedTerrain extends RuinTerrainKernel {
    static final int VERSION=1;
    final ForestFluidProtection fluids;
    ForestGradedTerrain(StructureTemplate template,BlockPos origin,StructurePlaceSettings settings,int[] approach){
        super(template,origin,settings,approach,ForestTerrainContract.of(template).terrain);
        fluids=ForestFluidProtection.at(ForestTerrainContract.of(template),new RuinPlacement(origin,settings.getRotation()));
    }
    @Override protected boolean allowsWrite(net.minecraft.world.level.WorldGenLevel level,BlockPos at,net.minecraft.world.level.block.state.BlockState next){return fluids.allows(level,at,next);}
    @Override protected boolean fluidColumn(net.minecraft.world.level.WorldGenLevel level,BlockPos top,int surface){return fluids.preservesColumn(level,top,surface);}
    @Override protected int writeFlags(){return net.minecraft.world.level.block.Block.UPDATE_CLIENTS|net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;}
    @Override protected int writeDepth(){return ForestRuinTerrain.DEPTH;}
    @Override protected boolean physicalSoftSurfaces(){return true;}
    @Override protected int finish(net.minecraft.world.level.WorldGenLevel level,net.minecraft.world.level.levelgen.structure.BoundingBox clip){
        // Keep new walkable lips lit even above the authored courtyard lights.
        int writes=0;for(long cell:savedGuards()){var at=BlockPos.of(cell);
            if(clip.isInside(at)&&ownGuard(at,level.getBlockState(at))){
                // A retained cave/hillside may expose a new walkable lip above
                // the authored lighting. Permanent light, never a harvestable fruit.
                var lamp=at.above();if(clip.isInside(lamp)&&level.isEmptyBlock(lamp)){
                    level.setBlock(lamp,net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LightBlock.LEVEL,7),writeFlags());writes++;
                }
            }
        }
        return writes;
    }
    static int[] sample(Structure.GenerationContext context,RuinPlacement placement,Map<BlockPos,Integer> known){
        var contract=ForestTerrainContract.of(context.structureTemplateManager().getOrCreate(RuinKind.FOREST.template(1))).terrain;
        return sample(contract,placement,(x,z)->context.chunkGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),context.randomState()),"FOREST_STRUCTURE_STARTS",known);
    }
}
