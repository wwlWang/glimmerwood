package cn.wwl.orimod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import java.util.Optional;

/** A single persisted chunk, not one candidate in every random-spread region. */
public final class GreatTreePlacement extends StructurePlacement {
    public static final MapCodec<GreatTreePlacement> CODEC=MapCodec.unit(GreatTreePlacement::new);
    public GreatTreePlacement(){super(Vec3i.ZERO,FrequencyReductionMethod.DEFAULT,1,115002,Optional.empty());}
    @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state,int x,int z){
        var site=GreatTreeSites.site(state.randomState());
        return site!=null && (site.origin().getX()>>4)==x && (site.origin().getZ()>>4)==z;
    }
    @Override public StructurePlacementType<?> type(){return OriWorldgen.GREAT_TREE_PLACEMENT.get();}
}
