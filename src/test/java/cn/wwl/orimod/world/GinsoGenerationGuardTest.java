package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class GinsoGenerationGuardTest {
    @TempDir Path region;
    @Test void wholeFootprintIncludesBothOuterCornersAndNegativeCoordinates(){
        for(var origin:new BlockPos[]{new BlockPos(8,64,8),new BlockPos(-8,70,-8)}){
            var visited=new HashSet<ChunkPos>();
            var clear=GinsoGenerationGuard.scan(origin,35,(x,z)->{visited.add(new ChunkPos(x,z));return null;});
            assertTrue(clear.allowed());assertEquals(25,clear.checked());
            for(var p:visited){
                var blocked=GinsoGenerationGuard.scan(origin,35,(x,z)->p.equals(new ChunkPos(x,z))?"old":null);
                assertFalse(blocked.allowed());assertEquals(p,blocked.blocked());
            }
        }
    }
    @Test void freshRegionSnapshotSeesNewWritesWithoutCreatingFiles()throws Exception{
        assertFalse(new GreatTreeSites.RegionHeaders(region).exists(-1,-1));
        try(var files=Files.list(region)){assertEquals(0,files.count());}
        var bytes=ByteBuffer.allocate(8192);bytes.putInt(1023*4,0x201);
        Files.write(region.resolve("r.-1.-1.mca"),bytes.array());
        assertTrue(new GreatTreeSites.RegionHeaders(region).exists(-1,-1));
        assertFalse(new GreatTreeSites.RegionHeaders(region).exists(-2,-1));
        Files.write(region.resolve("r.0.0.mca"),new byte[]{1});
        assertTrue(new GreatTreeSites.RegionHeaders(region).exists(0,0));
    }
    @Test void vanillaEmptyRegionIsEmptyButHalfAHeaderRemainsUnsafe()throws Exception{
        var file=region.resolve("r.0.0.mca");Files.write(file,new byte[0]);
        assertFalse(new GreatTreeSites.RegionHeaders(region).exists(0,0));
        Files.write(file,new byte[4096]);assertTrue(new GreatTreeSites.RegionHeaders(region).exists(0,0));
        var full=ByteBuffer.allocate(8192);full.putInt((4+8*32)*4,0x201);Files.write(file,full.array());
        var headers=new GreatTreeSites.RegionHeaders(region);assertTrue(headers.exists(4,8));assertFalse(headers.exists(5,8));
    }
    @Test void concurrentSaveBitsSurviveNegativeRegionBoundaries()throws Exception{
        var chunks=new GinsoGenerationGuard.SavedChunks();
        try(var pool=Executors.newFixedThreadPool(4)){
            for(int x=-40;x<40;x++)for(int z=-40;z<40;z++){var p=new ChunkPos(x,z);pool.submit(()->chunks.add(p));}
        }
        for(int x=-40;x<40;x++)for(int z=-40;z<40;z++)assertTrue(chunks.contains(x,z));
        assertFalse(chunks.contains(40,0));assertFalse(chunks.contains(-41,-40));
    }
}
