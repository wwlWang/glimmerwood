package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.RuinPiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import java.util.*;

/** Template-only tests need the same saved starts/references as real generated ruins. */
final class RuinMetadataFixture {
    static Runnable attach(ServerLevel level,RuinPiece piece){
        var restore=new ArrayList<Runnable>();var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(piece.kind().location());
        var origin=level.getChunkAt(piece.placement().origin());var starts=new HashMap<>(origin.getAllStarts());restore.add(()->origin.setAllStarts(starts));
        origin.setStartForStructure(structure,new StructureStart(structure,origin.getPos(),0,new PiecesContainer(List.of(piece))));
        var b=piece.getBoundingBox();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++){
            // placeInWorld has already materialized these chunks; do not generate additional ones for metadata.
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)throw new IllegalStateException("Fixture chunk not loaded "+x+","+z);
            var refs=new HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();chunk.getAllReferences().forEach((s,v)->refs.put(s,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));
            restore.add(()->chunk.setAllReferences(refs));chunk.addReferenceForStructure(structure,origin.getPos().toLong());
        }
        return ()->{for(int i=restore.size()-1;i>=0;i--)restore.get(i).run();};
    }
    private RuinMetadataFixture(){}
}
