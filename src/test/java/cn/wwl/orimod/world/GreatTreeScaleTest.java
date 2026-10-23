package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreatTreeScaleTest {
    @Test void oldVoxelHashesMatchCommitted993a88fExactly(){
        long[] seeds={115115,115116},expected={0x6f65e44f0a5467e1L,0x3e7838c892a39b79L};
        for(int i=0;i<seeds.length;i++){
            var s=new GreatTreeShape(seeds[i],1);long hash=0xcbf29ce484222325L;
            for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=-12;y<=82;y++)hash=(hash^s.cell(x,y,z))*0x100000001b3L;
            assertEquals(expected[i],hash,"An old partial tree would grow a different edge");
        }
    }
    @Test void allAzimuthsShowTheBroadCrownAndTwoSeparatedTiers(){
        for(long seed:new long[]{115115,115116}){
            var shape=new GreatTreeShape(seed);boolean[] layers=new boolean[225];
            double[] min=new double[12],max=new double[12];java.util.Arrays.fill(min,999);java.util.Arrays.fill(max,-999);
            int leaves=0,vines=0,strands=0;
            for(int x=-84;x<=84;x++)for(int z=-84;z<=84;z++)for(int y=0;y<=224;y++){
                byte c=shape.cell(x,y,z);
                if(c==GreatTreeShape.VINE){vines++;if(shape.cell(x,y+1,z)==GreatTreeShape.LEAF)strands++;}
                if(c!=GreatTreeShape.LEAF&&c!=GreatTreeShape.CORE)continue;leaves++;layers[y]=true;
                for(int i=0;i<12;i++){double p=x*Math.cos(i*Math.PI/12)+z*Math.sin(i*Math.PI/12);min[i]=Math.min(min[i],p);max[i]=Math.max(max[i],p);}
            }
            for(int i=0;i<12;i++)assertTrue(max[i]-min[i]>=140&&max[i]-min[i]<=160,"Narrow/oversized view at "+i+": "+(max[i]-min[i]));
            int gaps=0,run=0;boolean started=false;
            for(boolean present:layers){if(present){if(started&&run>=6){assertTrue(run<=12,"Detached crown tier "+run);gaps++;}started=true;run=0;}else if(started)run++;}
            assertEquals(2,gaps,"Need three leaf tiers with visible vertical gaps");
            assertTrue(leaves<110000,"Hidden overlapping crown shells returned: "+leaves);
            assertTrue(strands>0&&strands<=18*8,"Unbounded vines "+strands);
            assertTrue(vines<=strands*16,"Overlong curtains "+vines);
        }
    }
    @Test void silhouetteHasTwoHundredBlockHeightAndBroadThreeTierCrown(){
        for(int seed=0;seed<8;seed++){
            var shape=new GreatTreeShape(seed);int minX=999,maxX=-999,minZ=999,maxZ=-999,top=0,leaves=0,low=0,middle=0,high=0;
            for(int x=-84;x<=84;x++)for(int z=-84;z<=84;z++)for(int y=-16;y<=224;y++){
                byte c=shape.cell(x,y,z);
                if(Math.abs(x)==84||Math.abs(z)==84||y==224)assertEquals(0,c,"Clipped large silhouette");
                if(c!=GreatTreeShape.LEAF&&c!=GreatTreeShape.CORE)continue;
                leaves++;top=Math.max(top,y);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                if(y<145)low++;else if(y<185)middle++;else high++;
            }
            assertTrue(top>=205&&top<=220,"Top "+top);
            assertTrue(maxX-minX>=140&&maxX-minX<=160,"X span "+(maxX-minX));
            assertTrue(maxZ-minZ>=140&&maxZ-minZ<=160,"Z span "+(maxZ-minZ));
            assertTrue(low>middle&&middle>high&&high>1500,"Missing distinct crown tier");
            assertTrue(leaves>25000&&leaves<180000,"Unbounded solid foliage volume "+leaves);
        }
    }
    @Test void leafBackingIsOpaqueTexturedAndHasABloomVariant() throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("orimod.projectDir",".")).resolve("src/main/resources/assets/orimod");
        for(String suffix:new String[]{"","_bloomed"}){
            var texture=javax.imageio.ImageIO.read(root.resolve("textures/block/great_tree_leaf_core"+suffix+".png").toFile());
            assertEquals(16,texture.getWidth());assertEquals(16,texture.getHeight());var colours=new java.util.HashSet<Integer>();
            for(int x=0;x<16;x++)for(int y=0;y<16;y++){int pixel=texture.getRGB(x,y);assertEquals(255,pixel>>>24);colours.add(pixel);}
            assertTrue(colours.size()>8,"Backing lost its leaf grain");
            var model=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("models/block/great_tree_leaf_core"+suffix+".json"))).getAsJsonObject();
            assertEquals("minecraft:solid",model.get("render_type").getAsString());
        }
        var shape=new GreatTreeShape(115115);int core=0,cutout=0,backed=0,columns=0;
        for(int x=-80;x<=80;x++)for(int z=-80;z<=80;z++){
            int bottom=-1;
            for(int y=85;y<=224;y++){
                byte c=shape.cell(x,y,z);if(c==GreatTreeShape.CORE)core++;
                if(c==GreatTreeShape.LEAF){cutout++;if(bottom<0)bottom=y;}
            }
            if(bottom<0)continue;columns++;
            for(int y=bottom+1;y<=bottom+4;y++)if(shape.cell(x,y,z)==GreatTreeShape.CORE){backed++;break;}
        }
        assertTrue(core>30000&&cutout>30000,"Need both opaque backing and cutout skin");
        assertTrue(backed>columns*.85,"Unbacked lower canopy "+backed+" / "+columns);
    }
    @Test void taperAndHollowBoleReserveTheLowerHallAndUpperShaft(){
        assertTrue(GreatTreeShape.trunkRadius(8)>=15&&GreatTreeShape.trunkRadius(8)<=16);
        assertTrue(GreatTreeShape.trunkRadius(145)>=11&&GreatTreeShape.trunkRadius(145)<=12);
        var s=new GreatTreeShape(115115);
        for(int y=0;y<=185;y++)assertEquals(GreatTreeShape.AIR,s.cell((int)Math.round(GreatTreeShape.bendX(y)),y,(int)Math.round(GreatTreeShape.bendZ(y))));
        for(int z=-22;z<=0;z++)for(int x=-3;x<=3;x++)for(int y=0;y<6;y++)assertEquals(GreatTreeShape.AIR,s.cell(x,y,z));
    }
    @Test void oldVersionKeepsOldExtentAndVoxelLayoutEvenWhenNewShapeIsCached(){
        var old=GreatTreeSites.shape(115115,1);var large=GreatTreeSites.shape(115115,2);var reference=new LegacyGreatTreeShape(115115);
        assertNotSame(old,large);assertSame(old,GreatTreeSites.shape(115115,1));
        for(int x=-42;x<=42;x++)for(int z=-42;z<=42;z++)for(int y=-16;y<=90;y++)assertEquals(reference.cell(x,y,z),old.cell(x,y,z));
        var at=new BlockPos(8,70,-8);
        var oldSite=new GreatTreeSite.Site(at,BlockPos.ZERO,1,1);var newSite=new GreatTreeSite.Site(at,BlockPos.ZERO,1,2);
        assertFalse(oldSite.covers(49,0,0));assertTrue(newSite.covers(92,0,0));assertFalse(newSite.covers(93,0,0));
        assertEquals(82,GreatTreeShape.maxY(1));assertEquals(224,GreatTreeShape.maxY(2));
        assertThrows(IllegalArgumentException.class,()->GreatTreeSites.shape(1,99));
    }
    @Test void eachVoxelBelongsToExactlyOneNegativeOrPositiveChunkSlice(){
        var s=new GreatTreeShape(115116);long full=0,sliced=0;
        for(int x=-84;x<=84;x++)for(int z=-84;z<=84;z++)for(int y=-16;y<=224;y++)full+=s.cell(x,y,z);
        for(int cx=(-84+8)>>4;cx<=(84+8)>>4;cx++)for(int cz=(-84+8)>>4;cz<=(84+8)>>4;cz++)
            for(int x=Math.max(-84,cx*16-8);x<=Math.min(84,cx*16+15-8);x++)for(int z=Math.max(-84,cz*16-8);z<=Math.min(84,cz*16+15-8);z++)
                for(int y=-16;y<=224;y++)sliced+=s.cell(x,y,z);
        assertEquals(full,sliced);
    }
    @Test void skyGapsAndUncoveredForksSurviveTheScaleChange(){
        var s=new GreatTreeShape(115115);int gaps=0,arms=0,longestGap=0,gapLayers=0;
        // Large overlapping masses retain readable inter-mass windows, rather than maximising empty pixels.
        for(int y=60;y<=224;y++){
            boolean[] foliage=new boolean[169],wood=new boolean[169];
            for(int x=-84;x<=84;x++)for(int z=-84;z<=84;z++){
                byte c=s.cell(x,y,z);foliage[x+84]|=c==GreatTreeShape.LEAF||c==GreatTreeShape.CORE;wood[x+84]|=c==1||c==5;
                if(c==3)assertTrue(s.cell(x,y+1,z)==2||s.cell(x,y+1,z)==3);
            }
            int first=0,last=168;while(first<168&&!foliage[first])first++;while(last>0&&!foliage[last])last--;
            int run=0,layerGap=0;
            for(int x=first+3;x<last-3;x++){
                if(!foliage[x]&&!wood[x]){gaps++;layerGap=Math.max(layerGap,++run);}else run=0;
            }
            longestGap=Math.max(longestGap,layerGap);if(layerGap>=4)gapLayers++;
            for(int x=0;x<169;x++)if(Math.abs(x-84)>18&&!foliage[x]&&wood[x])arms++;
        }
        assertTrue(longestGap>=8&&gapLayers>=6,"No readable inter-mass windows: "+longestGap+" / "+gapLayers);assertTrue(arms>400,"Hidden arms "+arms);
    }
    @Test void broadDryFootprintRejectsShorelinesAndUnevenRootGround(){
        assertTrue(GreatTreeSiteRules.column(70,70,71,68,76,true,true,63));
        assertTrue(GreatTreeSiteRules.column(61,64,70,61,70,false,false,63));
        assertFalse(GreatTreeSiteRules.column(61,64,70,61,70,true,false,63));
        assertTrue(GreatTreeSiteRules.column(70,73,70,70,70,false,false,63));
        assertFalse(GreatTreeSiteRules.column(70,73,70,70,70,true,false,63));
        assertTrue(GreatTreeSiteRules.canopy(60,100));assertFalse(GreatTreeSiteRules.canopy(61,100));
        assertFalse(GreatTreeSiteRules.canopy(0,0));
        assertFalse(GreatTreeSiteRules.column(76,76,70,67,78,true,false,63));
        assertFalse(GreatTreeSiteRules.column(75,75,70,70,75,true,true,63));
    }
    @Test void onlyExistingWorldsCanWidenBeyondEightHundred(){
        assertEquals(800,GreatTreeSiteRules.searchLimit(0));
        assertEquals(2500,GreatTreeSiteRules.searchLimit(24000));
    }
    @Test void sanctuaryBlocksOnlyNaturalHostilesWithinNewFootprint(){
        var site=new GreatTreeSite.Site(new BlockPos(8,70,8),BlockPos.ZERO,1,2);
        for(var reason:MobSpawnType.values())assertEquals(reason==MobSpawnType.NATURAL||reason==MobSpawnType.CHUNK_GENERATION,
                GreatTreeSanctuary.refuses(site,reason,true,70,75,8),reason.toString());
        assertFalse(GreatTreeSanctuary.refuses(site,MobSpawnType.NATURAL,false,70,75,8));
        assertFalse(GreatTreeSanctuary.refuses(site,MobSpawnType.NATURAL,true,93,75,8));
        assertFalse(GreatTreeSanctuary.refuses(site,MobSpawnType.NATURAL,true,70,40,8));
        assertFalse(GreatTreeSanctuary.refuses(null,MobSpawnType.NATURAL,true,70,75,8));
        assertFalse(GreatTreeSanctuary.protects(new GreatTreeSite.Site(site.origin(),BlockPos.ZERO,1,1),8,75,8));
    }
}
