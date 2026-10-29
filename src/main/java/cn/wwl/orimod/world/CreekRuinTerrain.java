package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** The accepted creek contract; all write ordering lives in the shared kernel. */
final class CreekRuinTerrain extends RuinTerrainKernel {
    CreekRuinTerrain(StructureTemplate template,BlockPos origin,StructurePlaceSettings settings,int[] approach){
        super(template,origin,settings,approach);
    }
    static BoundingBox bounds(BoundingBox b){return RuinTerrainKernel.bounds(b);}
}
