package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreekTerrainExceptionsTest {
    @Test void classificationIsInvariantForFillCutAndPreservedExceptions(){
        int cases=0;
        for(int natural=-16;natural<=32;natural++)for(int planned=-16;planned<=32;planned++)
            for(int actual=-48;actual<=64;actual++)for(boolean building:new boolean[]{false,true})for(boolean tree:new boolean[]{false,true}){
                boolean cave=CreekTerrainExceptions.cave(natural,planned,actual);
                int after=building||cave||actual>planned+6||tree&&actual>planned?actual:planned;
                assertEquals(cave,CreekTerrainExceptions.cave(natural,planned,after));
                if(building||cave)assertEquals(actual,after);
                cases++;
            }
        assertEquals(1_085_252,cases);
    }
    @Test void authoredHighBankIsNotAnUnexpectedCave(){
        assertFalse(CreekTerrainExceptions.cave(0,15,0));
        assertFalse(CreekTerrainExceptions.cave(9,5,-1));
        assertTrue(CreekTerrainExceptions.cave(9,5,-2));
    }
}
