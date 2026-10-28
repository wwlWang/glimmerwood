package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreekHeightGridTest {
    @Test void bilinearPlaneIsExactIncludingNegativeCoordinatesAndCornersAreReused(){
        var calls=new HashSet<Long>();
        var g=new CreekHeightGrid((x,z)->{assertTrue(calls.add(((long)x<<32)^(z&0xffffffffL)),"Repeated noise column");return 90+2*x-z;});
        for(int z=-20;z<60;z++)for(int x=-20;x<60;x++)assertEquals(90+2*x-z,g.at(x,z));
        assertEquals(calls.size(),g.samples());assertTrue(g.samples()<150);
    }
    @Test void packagedContractOnlySamplesRimAndItsOutsideSeam() throws Exception {
        try(var stream=getClass().getResourceAsStream("/orimod/ruin_layout/creek_ruins_terrain_v1.json")){
            var json=JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
            var cols=new ArrayList<CreekTerrainRules.Ground>();
            for(var v:json.getAsJsonArray("columns")){var a=v.getAsJsonArray();cols.add(new CreekTerrainRules.Ground(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}
            var rules=new CreekTerrainRules(45,67,cols,22,2,50);var grid=new CreekHeightGrid((x,z)->9);
            int needed=0;
            for(int z=-20;z<87;z++)for(int x=-20;x<65;x++)if(rules.needsNatural(x,z)){grid.at(x,z);needed++;assertEquals(CreekTerrainRules.NONE,rules.ground(x,z));}
            assertTrue(needed<1800,"Sampled internal/far terrain");assertTrue(grid.samples()<=100,"Noise corner budget: "+grid.samples());
            for(int z=-20;z<87;z++)for(int x=-20;x<65;x++)if(rules.readOnlySeam(x,z)){
                assertFalse(rules.rim(x,z));assertEquals(CreekTerrainRules.NONE,rules.ground(x,z));
                assertFalse(rules.entrance(x,z));
            }
            var plan=rules.plan(-8,-8,52,74,(x,z)->{assertTrue(rules.needsNatural(x,z),"Unneeded natural source "+x+","+z);return grid.at(x,z);},(x,z)->CreekTerrainRules.NONE);
            assertFalse(plan.isEmpty());assertFalse(rules.needsNatural(22,30));assertFalse(rules.needsNatural(-20,-20));
        }
    }
    @Test void coarseGridDoesNotPretendToCatchEveryNarrowDip(){
        // Correctness is supplied independently by the untouched-column safety guard.
        var narrow=new CreekHeightGrid((x,z)->x==50?67:70);
        assertEquals(70,narrow.at(50,16));
    }
    @Test void zeroWeightCornersDoNotRequestNoiseColumns(){
        var grid=new CreekHeightGrid((x,z)->70);
        assertEquals(70,grid.at(8,8));assertEquals(1,grid.samples());
        assertEquals(70,grid.at(10,8));assertEquals(2,grid.samples());
    }
}
