package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Separate diagnostic run: capture post-carver actual terrain before ANY feature/write. */
final class CreekWaterBaselineAudit {
    static void started(ServerStartedEvent event){
        var server=event.getServer();var l=server.overworld();var out=new LinkedHashMap<String,Object>();
        try{
            out.put("caveProofNegativeControls",CreekForeignTerrainTrace.verifyCaveProofContract());
            var holder=l.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,RuinKind.CREEK.location()));
            var from=CreekWaterSourceTrace.ENABLED?l.getSharedSpawnPos():new BlockPos(Integer.getInteger("orimod.waterAudit.round",4)*4096,110,Integer.getInteger("orimod.waterAudit.round",4)*3072);
            var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),from,100,false);
            if(found==null)throw new IllegalStateException("No creek");
            var cp=new ChunkPos(found.getFirst());var start=l.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
            var piece=start.getPieces().stream().filter(RuinPiece.class::isInstance).map(RuinPiece.class::cast).findFirst().orElseThrow();
            var p=piece.placement();var b=piece.getBoundingBox();
            out.put("seed",l.getSeed());out.put("origin",List.of(p.origin().getX(),p.origin().getY(),p.origin().getZ()));out.put("rotation",p.rotation().name());
            var chunks=new LinkedHashMap<ChunkPos,ChunkAccess>();
            for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++){
                var c=l.getChunk(x,z,ChunkStatus.CARVERS,true);
                if(c.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES))throw new IllegalStateException("Before sample already decorated: "+c.getPos());
                chunks.put(c.getPos(),c);
            }
            out.put("beforeStage","CARVERS; every chunk asserted before FEATURES");
            var baseline=CreekNaturalBaseline.capture(l,piece);
            var columns=new ArrayList<Map<String,Object>>();var snapshots=new HashMap<BlockPos,List<String>>();
            var waterBefore=new HashSet<BlockPos>();var waterAfter=new HashSet<BlockPos>();
            for(var q:CreekTerrainGameTests.scanColumns()){
                var states=new ArrayList<String>();int top=-33;
                for(int y=-32;y<=40;y++){
                    var at=p.block(q.atY(y));var state=chunks.get(new ChunkPos(at)).getBlockState(at);
                    states.add(state.toString());
                    if(CreekTerrainSurface.ground(state,l,at))top=y;
                    if(state.getFluidState().is(Fluids.WATER)||state.getFluidState().is(Fluids.FLOWING_WATER))waterBefore.add(q.atY(y));
                }
                snapshots.put(q,states);var row=new LinkedHashMap<String,Object>();row.put("local",List.of(q.getX(),q.getZ()));row.put("groundBefore",top);row.put("before",states);columns.add(row);
            }
            out.put("columns",columns);out.put("status","before_captured");write(out);
            for(var c:chunks.keySet())l.getChunk(c.x,c.z,ChunkStatus.FULL,true);
            for(var row:columns){var a=(List<?>)row.get("local");var q=new BlockPos((Integer)a.get(0),0,(Integer)a.get(1));var states=new ArrayList<String>();int top=-33;
                for(int y=-32;y<=40;y++){
                    var at=p.block(q.atY(y));var state=l.getBlockState(at);states.add(state.toString());
                    if(CreekTerrainSurface.ground(state,l,at))top=y;
                    if(state.getFluidState().is(Fluids.WATER)||state.getFluidState().is(Fluids.FLOWING_WATER))waterAfter.add(q.atY(y));
                }
                row.put("groundAfter",top);row.put("after",states);
            }
            var removed=new HashSet<>(waterBefore);removed.removeAll(waterAfter);var added=new HashSet<>(waterAfter);added.removeAll(waterBefore);
            out.put("waterBefore",waterBefore.size());out.put("waterAfter",waterAfter.size());out.put("waterRemoved",positions(removed));out.put("waterAdded",positions(added));
            out.put("gradingWaterWrites",CreekWaterWrites.snapshot());
            if(CreekWaterSourceTrace.ENABLED||CreekWaterSourceTrace.FOREIGN){
                out.put("sourceWrites",CreekWaterSourceTrace.snapshot());
                out.put("beforeCreekWrites",CreekWaterSourceTrace.beforeSnapshots());
                if(CreekWaterSourceTrace.snapshot().isEmpty())throw new IllegalStateException("Target source-water writer was not observed");
            }
            out.put("naturalTerrain",CreekTerrainGameTests.naturalScan(l,piece,baseline));out.put("status","complete");
            CreekFixtureChunks.settle(l,"water-baseline");
        }catch(Exception e){out.put("status","failed");out.put("error",e.toString());e.printStackTrace();}
        finally{write(out);server.halt(false);}
    }
    private static List<List<Integer>> positions(Set<BlockPos> ps){return ps.stream().sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY)).map(p->List.of(p.getX(),p.getY(),p.getZ())).toList();}
    private static void write(Map<String,Object> out){try{Files.writeString(ReportFiles.output(Path.of("water-baseline.json")),new GsonBuilder().setPrettyPrinting().create().toJson(out));}catch(Exception e){throw new IllegalStateException(e);}}
}
