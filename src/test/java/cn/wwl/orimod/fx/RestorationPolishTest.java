package cn.wwl.orimod.fx;

import cn.wwl.orimod.config.VisualQuality;
import cn.wwl.orimod.progression.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RestorationPolishTest {
    @Test void canopyStaysSaturatedWithSmallFlowersAndPreservesEveryCutoutHole()throws Exception{
        var root=Path.of(System.getProperty("orimod.projectDir"),"src/main/resources/assets/orimod/textures/block");
        var normal=ImageIO.read(root.resolve("spirit_leaves.png").toFile());var bloom=ImageIO.read(root.resolve("spirit_leaves_bloomed.png").toFile());
        int warm=0,flowers=0,solid=0;double oldSaturation=0,newSaturation=0;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int n=normal.getRGB(x,y),b=bloom.getRGB(x,y);assertEquals(n>>>24,b>>>24);
            if((b>>>24)>0){solid++;if((b>>16&255)>(b&255)+25 && (b>>8&255)>180)warm++;
                if((b>>16&255)>230 && (b>>8&255)>230)flowers++;
                oldSaturation+=saturation(n);newSaturation+=saturation(b);}
        }
        assertEquals(4,warm,"Four small gold centres");assertEquals(16,flowers,"Four 2x2 flowers");
        assertTrue(flowers<solid*.18,"Flowers should punctuate, not bleach the canopy");assertTrue(newSaturation>=oldSaturation);
        var model=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.getParent().getParent()
                .resolve("models/block/spirit_leaves_bloomed.json"))).getAsJsonObject();
        assertFalse(model.get("ambientocclusion").getAsBoolean());
        for(var element:model.getAsJsonArray("elements"))assertFalse(element.getAsJsonObject().get("shade").getAsBoolean());
    }
    private static double saturation(int rgb){int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255,max=Math.max(r,Math.max(g,b));return max==0?0:1-Math.min(r,Math.min(g,b))/(double)max;}
    @Test void columnTextureHasBroadBrightCoreAndTransparentEdges()throws Exception{
        var image=ImageIO.read(Path.of(System.getProperty("orimod.projectDir"),"src/main/resources/assets/orimod/textures/misc/restoration_column.png").toFile());
        int bright=0;for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,3)>>>24)>127)bright++;
        assertTrue(bright>=image.getWidth()/2,"A hairline texture defeats the world-unit width");
        assertTrue((image.getRGB(0,3)>>>24)<3);assertTrue((image.getRGB(63,3)>>>24)<3);
        for(int x=0;x<64;x++)assertEquals(image.getRGB(x,0),image.getRGB(x,7),"Slices must not restart a vertical texture fade");
    }
    @Test void columnFadesMonotonicallyToZeroAndKeepsAVisibleWorldWidth(){
        assertTrue(RestorationVisuals.CORE_WIDTH>=1.5 && RestorationVisuals.CORE_WIDTH<=2);
        assertTrue(RestorationVisuals.HALO_WIDTH>RestorationVisuals.CORE_WIDTH);assertEquals(1,RestorationVisuals.columnFade(0));
        float previous=1;for(int y=1;y<=48;y++){float value=RestorationVisuals.columnFade(y);assertTrue(value<=previous);previous=value;}assertEquals(0,previous);
    }
    @Test void threeLightsHaveDistinctColourFamilies(){
        int heart=WillWisp.HEART.color,wing=WillWisp.WING.color,earth=WillWisp.EARTH.color;
        assertTrue((heart>>16&255)>(heart&255)+60);
        assertTrue((wing&255)>(wing>>8&255)+60);
        assertTrue((earth>>8&255)>(earth>>16&255)+60);
    }
    @Test void absorbedWispsFadeInsteadOfStackingThreeWhiteCores(){for(int i=0;i<3;i++){
        assertEquals(0,RestorationVisuals.wispTravel(i,60+i*20));assertEquals(1,RestorationVisuals.wispTravel(i,95+i*20));
        assertEquals(1,RestorationVisuals.wispAlpha(i,60+i*20));assertEquals(0,RestorationVisuals.wispAlpha(i,105+i*20));
    }}
    @Test void viewpointCandidatesStayFourToSixBlocksOutsideNorthEntrance(){var heart=new BlockPos(100,64,100);
        for(var p:RestorationViewpoint.candidates(heart)){int outside=heart.getZ()-3-p.getZ();assertTrue(outside>=4&&outside<=6);assertTrue(Math.abs(p.getX()-heart.getX())<=1);assertTrue(Math.abs(p.getY()-heart.getY())<=1);}
    }
    @Test void sparkleQueueDeduplicatesAndHonoursQualityBudget(){var q=new BloomSparkQueue();for(int i=0;i<100;i++){q.offer(i,0);q.offer(i,0);}assertEquals(100,q.size());
        for(var quality:VisualQuality.values()){int n=FxBudget.bloomSparks(quality,true);assertTrue(n<=FxBudget.MAX_PER_BURST);assertEquals(n,q.drain(0,n).size());assertEquals(0,FxBudget.bloomSparks(quality,false));}
        assertEquals(48,q.drain(0,Integer.MAX_VALUE).size());assertEquals(4,q.size());
    }
    @Test void sparkleQueueIsBoundedExpiresAndClearsOnLogout(){var q=new BloomSparkQueue();for(int i=0;i<3000;i++)q.offer(i,0);assertEquals(BloomSparkQueue.CAPACITY,q.size());
        assertTrue(q.drain(BloomSparkQueue.MAX_AGE+1,48).isEmpty());assertEquals(0,q.size());q.offer(1,200);q.clear();assertEquals(0,q.size());
    }
}
