package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original shape assertions remain as version-1 save compatibility coverage. */
class GreatTreeTest {
    @TempDir Path dir;
    @Test void absentRegionsStayAbsentAndNegativeChunkIndicesUseFloorDivision() throws Exception {
        var headers=new GreatTreeSites.RegionHeaders(dir);
        assertFalse(headers.exists(0,0));assertFalse(Files.exists(dir.resolve("r.0.0.mca")));
        var bytes=ByteBuffer.allocate(8192);bytes.putInt((31+31*32)*4,0x00000201);
        Files.write(dir.resolve("r.-1.-1.mca"),bytes.array());
        assertTrue(headers.exists(-1,-1));assertFalse(headers.exists(-2,-1));
    }
    @Test void damagedRegionHeadersRejectInsteadOfRiskingAnOldBuild() throws Exception {
        Files.write(dir.resolve("r.0.0.mca"),new byte[30]);
        assertTrue(new GreatTreeSites.RegionHeaders(dir).exists(4,8));
    }
    @Test void uniqueSiteAndGenerationLatchSurviveSaveReload() {
        var data=new GreatTreeSite();var site=new GreatTreeSite.Site(new BlockPos(-328,75,568),new BlockPos(12,64,4),115115,1);
        data.choose(site);data.begun(true);
        var reload=GreatTreeSite.load(data.save(new CompoundTag(),null),null);
        assertEquals(site,reload.site());assertTrue(reload.begun());
        assertNull(GreatTreeSite.load(new CompoundTag(),null).site());
    }
    @Test void exclusionIncludesFullFootprintAndRequestedNeighbourMargin() {
        var s=new GreatTreeSite.Site(new BlockPos(8,70,-8),BlockPos.ZERO,1,1);
        assertTrue(s.covers(48,32,0));assertFalse(s.covers(49,32,0));assertTrue(s.covers(60,44,12));
    }
    @Test void shapeHasLandmarkScaleAndAnOpenHollowEntranceAcrossSeeds() {
        for(int seed=0;seed<8;seed++){
            var s=new LegacyGreatTreeShape(seed);int top=0,minX=100,maxX=-100,minZ=100,maxZ=-100,wood=0,leaves=0;
            for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=-12;y<=82;y++){
                byte cell=s.cell(x,y,z);if(cell==GreatTreeShape.WOOD || cell==GreatTreeShape.VEIN)wood++;
                if(cell==GreatTreeShape.LEAF){leaves++;top=Math.max(top,y);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
                if(Math.abs(x)==40||Math.abs(z)==40||y==82)assertEquals(GreatTreeShape.EMPTY,cell,"Clipped silhouette");
            }
            assertTrue(top>=60&&top<=80,"height="+top);assertTrue(maxX-minX>=60&&maxX-minX<=74,"Crown X span="+(maxX-minX));
            assertTrue(maxZ-minZ>=60&&maxZ-minZ<=74,"Crown Z span="+(maxZ-minZ));
            assertTrue(wood>5000&&leaves>8000,"Not a substantial landmark");
            for(int z=-11;z<=0;z++)for(int x=-2;x<=2;x++)for(int y=0;y<5;y++)assertEquals(GreatTreeShape.AIR,s.cell(x,y,z));
            for(int y=1;y<39;y++)assertEquals(GreatTreeShape.AIR,s.cell((int)Math.round(LegacyGreatTreeShape.bendX(y)),y,(int)Math.round(LegacyGreatTreeShape.bendZ(y))));
        }
    }
    @Test void independentlyVisitedChunkSlicesGiveExactlyTheSameVoxels() {
        var s=new LegacyGreatTreeShape(115115);var again=new LegacyGreatTreeShape(115115);
        long full=0,sliced=0;int offsetX=8,offsetZ=8;
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=-12;y<=82;y++){
            assertEquals(s.cell(x,y,z),again.cell(x,y,z));full+=s.cell(x,y,z);
        }
        // Same origin-relative coordinates as postProcess, including the edges crossing negative chunks.
        for(int cx=-2;cx<=3;cx++)for(int cz=-2;cz<=3;cz++)
            for(int x=Math.max(-40,cx*16-offsetX);x<=Math.min(40,cx*16+15-offsetX);x++)
                for(int z=Math.max(-40,cz*16-offsetZ);z<=Math.min(40,cz*16+15-offsetZ);z++)
                    for(int y=-12;y<=82;y++)sliced+=s.cell(x,y,z);
        assertEquals(full,sliced);
    }
    @Test void allVineSegmentsHaveLeafOrVineDirectlyAbove(){
        var s=new LegacyGreatTreeShape(115116);
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=0;y<82;y++)
            if(s.cell(x,y,z)==GreatTreeShape.VINE)assertTrue(s.cell(x,y+1,z)==GreatTreeShape.VINE||s.cell(x,y+1,z)==GreatTreeShape.LEAF);
    }
    @Test void tiersExposeSkyWindowsAndTheirSupportingArms(){
        for(int seed=0;seed<8;seed++){
            var s=new LegacyGreatTreeShape(seed);int gaps=0,arms=0;
            for(int y=26;y<=58;y++){
                boolean[] foliage=new boolean[81],wood=new boolean[81];
                for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++){
                    byte c=s.cell(x,y,z);foliage[x+40]|=c==GreatTreeShape.LEAF;
                    wood[x+40]|=c==GreatTreeShape.WOOD||c==GreatTreeShape.VEIN;
                }
                int first=0,last=80;while(first<80&&!foliage[first])first++;while(last>0&&!foliage[last])last--;
                for(int x=first+2;x<last-2;x++)if(!foliage[x]&&!wood[x])gaps++;
                for(int x=0;x<81;x++)if(Math.abs(x-40)>8&&!foliage[x]&&wood[x])arms++;
            }
            assertTrue(gaps>=40,"Canopy merged into one dome: sky="+gaps);
            assertTrue(arms>=60,"Arms buried in foliage: visible="+arms);
        }
    }
    @Test void flaredLowerBoleAndVeinsReachRootsTrunkAndArms(){
        var s=new LegacyGreatTreeShape(115115);int[] veins=new int[3];
        assertTrue(LegacyGreatTreeShape.trunkRadius(8)>=7.3);assertTrue(LegacyGreatTreeShape.trunkRadius(40)<=4.9);
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=0;y<=74;y++)
            if(s.cell(x,y,z)==GreatTreeShape.VEIN)veins[y<14?0:y<28?1:2]++;
        for(int n:veins)assertTrue(n>=20,"Missing segment of luminous veins");
    }
}
