package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoCanopyTest {
    @Test void sparseCurtainsAndOpaqueBackingStayWithinTheReviewedFaceBudget(){
        int[] oldVines={4756,4521};int sample=0;
        for(long seed:new long[]{115117,115118}){
            var s=new GinsoShape(seed,3);int leaf=0,core=0,vines=0,faces=0;
            int[][] directions={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++)for(int y=-20;y<=124;y++){
                if(s.cell(x,y,z)==GinsoShape.VINE)vines++;
                if(s.cell(x,y,z)!=GinsoShape.LEAF)continue;
                leaf++;boolean dense=s.coreLeaf(x,y,z);if(dense)core++;
                for(var d:directions){int nx=x+d[0],ny=y+d[1],nz=z+d[2];byte next=s.cell(nx,ny,nz);
                    if(!GinsoShape.timber(next)&&(dense?!s.coreLeaf(nx,ny,nz):next!=GinsoShape.LEAF))faces++;
                }
            }
            assertTrue(leaf>3000&&core>leaf*.35,"Keep a substantial solid moss skirt");
            assertTrue(faces<13000,"Internal foliage faces leaked: "+faces);
            assertTrue(vines<=oldVines[sample++]*.6,"Reduce curtain faces by at least 40%: "+vines);
        }
        var old=new GinsoShape(115117,2);
        for(int x=-28;x<=28;x++)for(int z=-28;z<=28;z++)for(int y=-8;y<=64;y++)assertFalse(old.coreLeaf(x,y,z),"v2 must not acquire new backing states");
    }
    @Test void newCanopyAssetsKeepLegacyDropsAndIncludeInventoryAndLanguages()throws Exception{
        var root=Path.of(System.getProperty("orimod.projectDir","."));
        var assets=root.resolve("src/main/resources/assets/orimod");
        var loot=root.resolve("src/main/resources/data/orimod/loot_table/blocks");
        var old=JsonParser.parseString(Files.readString(loot.resolve("ginso_leaves.json")));
        for(String name:new String[]{"ginso_canopy","ginso_leaf_core"}){
            assertEquals(old,JsonParser.parseString(Files.readString(loot.resolve(name+".json"))));
            var model=JsonParser.parseString(Files.readString(assets.resolve("models/block/"+name+".json"))).getAsJsonObject();
            assertEquals("minecraft:"+(name.equals("ginso_canopy")?"cutout_mipped":"solid"),model.get("render_type").getAsString());
            assertTrue(Files.exists(assets.resolve("models/item/"+name+".json")));
            for(String lang:new String[]{"en_us","zh_cn"})assertTrue(JsonParser.parseString(Files.readString(assets.resolve("lang/"+lang+".json"))).getAsJsonObject().has("block.orimod."+name));
        }
    }
}
