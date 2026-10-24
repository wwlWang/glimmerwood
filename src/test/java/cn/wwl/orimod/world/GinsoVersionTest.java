package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

class GinsoVersionTest {
    private static final String ROOT="/cn/wwl/orimod/world/ginso-v2-42a50ec/";
    private static byte[] golden(String name)throws IOException{
        try(var in=new GZIPInputStream(GinsoVersionTest.class.getResourceAsStream(ROOT+name))){return in.readAllBytes();}
    }
    private static CompoundTag tag(long seed){
        var tag=new CompoundTag();tag.putLong("Origin",BlockPos.ZERO.asLong());tag.putLong("ShapeSeed",seed);
        tag.putInt("ShapeVersion",2);tag.putInt("CourseVersion",1);tag.putInt("O",-1);tag.putIntArray("BB",new int[]{-28,-8,-28,28,64,28});return tag;
    }
    private static GinsoPiece piece(CompoundTag tag){return new GinsoPiece((context,nbt)->null,tag);}
    @Test void v2TagLoadsEveryOldVoxelAndKeepsBothVersionsOnSave()throws Exception{
        byte[] course=golden("course-1.bin.gz");
        for(long seed:new long[]{115117,115118}){
            var p=piece(tag(seed));byte[] expected=golden("shape-"+seed+".bin.gz");
            assertEquals(2,p.shapeVersion());assertEquals(1,p.courseVersion());
            assertEquals(64,p.getBoundingBox().maxY());assertEquals(-28,p.getBoundingBox().minX());
            int i=0;for(int x=-28;x<=28;x++)for(int z=-28;z<=28;z++)for(int y=-8;y<=64;y++,i++){
                assertEquals(expected[i],p.sculpture().cell(x,y,z),"Legacy exterior at "+x+","+y+","+z);
                assertEquals(course[i],GinsoInterior.layout().cell(x,y,z),"Legacy course at "+x+","+y+","+z);
            }
            var saved=new CompoundTag();p.addAdditionalSaveData(null,saved);
            assertEquals(2,saved.getInt("ShapeVersion"));assertEquals(1,saved.getInt("CourseVersion"));
        }
    }
    @Test void legacySixtyFourSeedHashesAreThePreEditHeadBaseline()throws Exception{
        var json=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream(ROOT+"baseline.json"))).getAsJsonObject();
        assertEquals("42a50ec8dd7832b28fd766957a3238718b391836",json.get("head").getAsString());
        for(int seed=0;seed<64;seed++){
            var s=new GinsoShape(seed,2);var digest=MessageDigest.getInstance("SHA-256");
            for(int x=-28;x<=28;x++)for(int z=-28;z<=28;z++)for(int y=-8;y<=64;y++)digest.update(s.cell(x,y,z));
            assertEquals(json.getAsJsonObject("shapeHashes64Seeds").get(""+seed).getAsString(),HexFormat.of().formatHex(digest.digest()),"Seed "+seed);
        }
    }
    @Test void cacheSeparatesVersionsAndMissingCourseNeverInventsOne(){
        var old=GinsoPiece.shape(115117,2);var tall=GinsoPiece.shape(115117,3);
        assertNotSame(old,tall);assertSame(old,GinsoPiece.shape(115117,2));assertTrue(tall.height>old.height*1.8);
        var t=tag(115117);t.remove("ShapeVersion");t.remove("CourseVersion");var legacy=piece(t);
        assertEquals(2,legacy.shapeVersion());assertFalse(legacy.hasCourse());
        assertThrows(IllegalArgumentException.class,()->new GinsoShape(0,4));
    }
}
