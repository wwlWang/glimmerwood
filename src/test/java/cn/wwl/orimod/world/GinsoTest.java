package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class GinsoTest {
    @Test void riversRequireAtLeastSeventyPercentLandAndAtMostTwoDeep(){
        assertEquals(GinsoSiteRules.Reason.OK,GinsoSiteRules.terrain(61,63,3,10,2));
        assertEquals(GinsoSiteRules.Reason.WET_FOOTPRINT,GinsoSiteRules.terrain(61,63,4,10,2));
        assertEquals(GinsoSiteRules.Reason.DEEP_WATER,GinsoSiteRules.terrain(60,63,1,10,3));
        assertEquals(GinsoSiteRules.Reason.RELIEF,GinsoSiteRules.terrain(64,73,0,49,0));
        assertEquals(GinsoSiteRules.Reason.OK,GinsoSiteRules.terrain(64,72,0,49,0));
    }
    @Test void landmarksCannotShareTheThreeHundredBlockRadius(){
        assertFalse(GinsoSiteRules.awayFromGreatTree(299,0));assertTrue(GinsoSiteRules.awayFromGreatTree(300,0));
        assertFalse(GinsoSiteRules.awayFromGreatTree(-200,-200));assertTrue(GinsoSiteRules.awayFromGreatTree(-300,0));
    }
    @Test void sixtyFourSeedsKeepTowerShaftEntranceAndSilhouetteBounds(){
        for(int seed=0;seed<64;seed++){
            var s=new GinsoShape(seed,2);int wood=0,leaves=0,top=0;
            for(int x=-28;x<=28;x++)for(int z=-28;z<=28;z++)for(int y=-8;y<=64;y++){
                byte c=s.cell(x,y,z);
                if(Math.abs(x)==28||Math.abs(z)==28||y==64)assertEquals(GinsoShape.EMPTY,c,"Clipped sculpture, seed="+seed);
                if(c==GinsoShape.WOOD||c==GinsoShape.SAP){wood++;top=Math.max(top,y);}
                if(c==GinsoShape.LEAF)leaves++;
                if(c==GinsoShape.VINE)assertTrue(s.cell(x,y+1,z)==GinsoShape.VINE||s.cell(x,y+1,z)==GinsoShape.LEAF||GinsoShape.timber(s.cell(x,y+1,z)),"Unsupported hanging moss/drip");
            }
            assertTrue(top>=45&&top<=60,"Height="+top);assertTrue(wood>7000&&leaves>800&&leaves<4000);
            for(int y=1;y<=s.height;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)assertEquals(GinsoShape.AIR,s.cell(x,y,z),"Shaft obstructed");
            for(int z=-15;z<=0;z++)for(int x=-2;x<=2;x++)for(int y=1;y<=4;y++)assertEquals(GinsoShape.AIR,s.cell(x,y,z),"Entry obstructed");
        }
    }
    @Test void archedRootsLeaveGapsAndHighSnagsRemainExposed(){
        var s=new GinsoShape(115117,2);int gaps=0,bare=0;
        for(int x=-25;x<=25;x++)for(int z=-25;z<=25;z++){
            if(Math.hypot(x,z)<12)continue;
            if(s.cell(x,1,z)==0)for(int y=3;y<=9;y++)if(s.cell(x,y,z)==1){gaps++;break;}
            for(int y=40;y<=57;y++)if(s.cell(x,y,z)==1){boolean foliage=false;for(int yy=y;yy<60;yy++)foliage|=s.cell(x,yy,z)==2;if(!foliage)bare++;}
        }
        assertTrue(gaps>=60,"Stilt arches filled solid: "+gaps);assertTrue(bare>=50,"Snags hidden by crown: "+bare);
    }
    @Test void brokenForksReplaceTheChimneyAndCarryDimBioluminescence(){
        for(int seed=0;seed<64;seed++){
            var s=new GinsoShape(seed,2);int highCentre=0,leaningTip=0,sap=0,shelves=0,wood=0;
            for(int x=-28;x<=28;x++)for(int z=-28;z<=28;z++)for(int y=1;y<=60;y++){
                byte c=s.cell(x,y,z);
                if(GinsoShape.timber(c)){
                    wood++;
                    if(y>=s.height-7){if(Math.hypot(x,z)<9)highCentre++;if(Math.hypot(x,z)>12)leaningTip++;}
                }
                if(c==GinsoShape.SAP)sap++;
                if(c==GinsoShape.FUNGUS)shelves++;
            }
            assertTrue(highCentre<10,"Flat central chimney remains: "+highCentre);
            assertTrue(leaningTip>=8,"Tall leaning fork absent");
            assertTrue(sap>=150&&sap<wood/8,"Cracks must read without lighting the whole bole: "+sap);
            assertTrue(shelves>=90,"Luminous shelves absent: "+shelves);
        }
    }
    @Test void separateChunkVisitsDoNotChangeTheShape(){
        var a=new GinsoShape(115118,2);var b=new GinsoShape(115118,2);long full=0,sliced=0;
        for(int x=-28;x<=28;x++)for(int z=-28;z<=28;z++)for(int y=-8;y<=64;y++){assertEquals(a.cell(x,y,z),b.cell(x,y,z));full+=a.cell(x,y,z);}
        for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++)
            for(int x=Math.max(-28,cx*16-8);x<=Math.min(28,cx*16+7);x++)for(int z=Math.max(-28,cz*16-8);z<=Math.min(28,cz*16+7);z++)
                for(int y=-8;y<=64;y++)sliced+=a.cell(x,y,z);
        assertEquals(full,sliced);
    }
    @Test void placementDataKeepsRarityAndWetBiomeScopeExplicit()throws Exception{
        var root=Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/data/orimod");
        var placement=JsonParser.parseString(Files.readString(root.resolve("worldgen/structure_set/ginso_tree.json"))).getAsJsonObject().getAsJsonObject("placement");
        assertEquals(52,placement.get("spacing").getAsInt());assertEquals(20,placement.get("separation").getAsInt());
        var biomes=Files.readString(root.resolve("tags/worldgen/biome/has_ginso_tree.json"));
        assertTrue(biomes.contains("minecraft:swamp")&&biomes.contains("minecraft:river")&&biomes.contains("minecraft:dark_forest"));
        assertFalse(biomes.contains("minecraft:desert")||biomes.contains("is_overworld"));
    }
}
