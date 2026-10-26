package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Resolves the saved piece contract without loading neighbouring chunks or inferring an upgrade. */
public final class RuinLayouts {
    public record Site(RuinKind.Layout layout, RuinPlacement placement, int version) {}
    public static RuinPiece find(ServerLevel level, BlockPos shrine, java.util.function.Predicate<RuinPiece> matches) {
        var chunk=level.getChunkSource().getChunkNow(shrine.getX()>>4,shrine.getZ()>>4);
        var starts=new java.util.LinkedHashSet<StructureStart>();
        if(chunk!=null){
            starts.addAll(chunk.getAllStarts().values());
            for(var entry:chunk.getAllReferences().entrySet())if(entry.getKey() instanceof RuinStructure){
                for(long packed:entry.getValue()){
                    var pos=new ChunkPos(packed);var origin=level.getChunkSource().getChunkNow(pos.x,pos.z);
                    if(origin!=null){var start=origin.getStartForStructure(entry.getKey());if(start!=null)starts.add(start);}
                }
            }
        }
        for(var start:starts)if(start.isValid())for(var p:start.getPieces())if(p instanceof RuinPiece piece && matches.test(piece))return piece;
        return null;
    }
    public static Site atShrine(ServerLevel level, RuinKind kind, BlockPos shrine) {
        var piece=find(level,shrine,p->p.kind()==kind && p.placement().block(p.layout().shrine()).equals(shrine));
        if(piece!=null)return new Site(piece.layout(),piece.placement(),piece.layoutVersion());
        // Player-built shrines and old flat test fixtures have no structure start. State never upgrades an old shrine.
        var state=level.getBlockState(shrine);
        int version=kind==RuinKind.CAVE && cn.wwl.orimod.block.SanctuaryAssembly.assembled(state)?1:0;
        var layout=kind.layout(version);
        var rotation=state.hasProperty(RuinShrineBlock.FACING)?RuinPlacement.fromFacing(state.getValue(RuinShrineBlock.FACING)):net.minecraft.world.level.block.Rotation.NONE;
        return new Site(layout,new RuinPlacement(shrine.subtract(layout.shrine().rotate(rotation)),rotation),version);
    }
    private RuinLayouts() {}
}
