package cn.wwl.orimod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * R114: the cave altar template clears an 11x7x14 trench above its stairwell, which flattened the hillside into a
 * plaza once the altar sits under real ground. Keep only the doorway landing and the stair corridor's own headroom;
 * every other trench air cell is skipped so the natural ground (and its grass) stays as the stairwell's roof.
 * Local template coordinates (cave_altar has no pad); see cave() in scripts/art/build_ruins.py.
 */
public final class CaveEntranceProcessor extends StructureProcessor {
    public static final CaveEntranceProcessor INSTANCE = new CaveEntranceProcessor();
    public static final MapCodec<CaveEntranceProcessor> CODEC = MapCodec.unit(() -> INSTANCE);
    private CaveEntranceProcessor() {}

    /** True when the template's air at this local cell should be placed (doorway or stair headroom). */
    public static boolean keepsAir(int x, int y, int z) {
        boolean trench = x >= 6 && x <= 16 && y >= 12 && y <= 18 && z >= 0 && z <= 13;
        if (!trench) return true;
        boolean doorway = x >= 8 && x <= 14 && z <= 1;
        boolean corridor = x >= 9 && x <= 13 && z >= 2 && y <= Math.max(1, 13 - z) + 4;
        return doorway || corridor;
    }

    @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
            StructureTemplate.StructureBlockInfo raw, StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings) {
        var local = raw.pos();
        return placed.state().isAir() && !keepsAir(local.getX(), local.getY(), local.getZ()) ? null : placed;
    }

    @Override protected StructureProcessorType<?> getType() { return OriWorldgen.CAVE_ENTRANCE.get(); }
}
