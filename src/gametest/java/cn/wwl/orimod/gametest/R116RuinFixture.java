package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

/** Real saved-piece ownership, minimal blocks. All chunk requests have bounded tickets and synchronous completion. */
final class R116RuinFixture implements AutoCloseable {
    final RuinPiece piece;final List<BlockPos> doors,pedestals;final BlockPos altar;
    private final List<Runnable> cleanup=new ArrayList<>();
    private final List<ChunkPos> forced=new ArrayList<>();
    private final net.minecraft.server.level.ServerLevel level;
    R116RuinFixture(GameTestHelper h,int version,Rotation rotation,int offset){
        level=h.getLevel();var layout=RuinKind.CAVE.layout(version);
        var placement=RuinPlacement.atMinimum(layout,h.absolutePos(new BlockPos(40+offset,25,40)),rotation);
        piece=new RuinPiece(level.getStructureManager(),RuinKind.CAVE,placement,version);
        var doorStates=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        if(version==0)for(var p:layout.vault().door())doorStates.put(placement.block(p),OriBlocks.KEYSTONE_DOOR.get().defaultBlockState());
        else for(var cell:level.getStructureManager().getOrCreate(RuinKind.CAVE.template(version)).filterBlocks(placement.origin(),new StructurePlaceSettings().setRotation(rotation),OriBlocks.KEYSTONE_DOOR.get()))doorStates.put(cell.pos(),cell.state());
        doors=List.copyOf(doorStates.keySet());pedestals=layout.keystones().stream().map(placement::block).toList();altar=placement.block(layout.bossAltar());
        var points=new HashSet<>(doors);points.addAll(pedestals);points.add(altar);points.add(placement.origin());
        try{
            for(var cp:points.stream().map(ChunkPos::new).distinct().toList()){
                if(!level.getForcedChunks().contains(cp.toLong())){level.setChunkForced(cp.x,cp.z,true);forced.add(cp);}
                level.getChunk(cp.x,cp.z);
            }
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RuinKind.CAVE.location());
            var origin=level.getChunkAt(placement.origin());var starts=new HashMap<>(origin.getAllStarts());cleanup.add(()->origin.setAllStarts(starts));
            origin.setStartForStructure(structure,new StructureStart(structure,origin.getPos(),0,new PiecesContainer(List.of(piece))));
            for(var cp:points.stream().map(ChunkPos::new).distinct().toList()){
                var chunk=level.getChunk(cp.x,cp.z);var refs=new HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();
                chunk.getAllReferences().forEach((s,v)->refs.put(s,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));
                cleanup.add(()->chunk.setAllReferences(refs));chunk.addReferenceForStructure(structure,origin.getPos().toLong());
            }
            for(var e:doorStates.entrySet())level.setBlock(e.getKey(),e.getValue(),2);
            for(var p:pedestals)level.setBlock(p,OriBlocks.KEYSTONE_PEDESTAL.get().defaultBlockState(),2);
            level.setBlock(altar,OriBlocks.GLOOM_ALTAR.get().defaultBlockState(),2);
        }catch(RuntimeException e){close();throw e;}
    }
    @Override public void close(){try{for(int i=cleanup.size()-1;i>=0;i--)cleanup.get(i).run();cleanup.clear();}finally{CreekFixtureChunks.release(level,forced);}}
}
