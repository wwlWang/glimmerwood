package cn.wwl.orimod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

public final class GreatTreeStructure extends Structure {
    public static final MapCodec<GreatTreeStructure> CODEC=simpleCodec(GreatTreeStructure::new);
    public GreatTreeStructure(StructureSettings settings){super(settings);}
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext c){
        var site=GreatTreeSites.site(c.randomState());
        if(site==null || (site.origin().getX()>>4)!=c.chunkPos().x || (site.origin().getZ()>>4)!=c.chunkPos().z) return Optional.empty();
        return Optional.of(new GenerationStub(site.origin(),b->b.addPiece(new GreatTreePiece(site))));
    }
    @Override public StructureType<?> type(){return OriWorldgen.GREAT_TREE.get();}
}
