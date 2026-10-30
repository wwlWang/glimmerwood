package cn.wwl.orimod.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpiritHudRulesTest {
    @Test void cellsTrackRealHealthAndEnergy(){assertEquals(10,SpiritHudRules.cells(20,2));assertEquals(20,SpiritHudRules.cells(200,10));assertEquals("half",SpiritHudRules.fill(19,2,9));assertEquals("empty",SpiritHudRules.fill(0,2,0));assertEquals("full",SpiritHudRules.fill(20,2,9));}
    @Test void largeModdedStatsStayBounded(){for(float max:new float[]{40,200,1000,99999}){float unit=SpiritHudRules.unit(max,2);assertTrue(SpiritHudRules.cells(max,unit)<=20);assertTrue(unit*SpiritHudRules.cells(max,unit)>=max);}}
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={1,10,11,20})
    void tenCellsPerRowReserveSpaceAboveBothSides(int cells){
        int rows=SpiritHudRules.rows(cells),baseline=200;
        assertEquals(cells<=10?1:2,rows);
        for(int i=0;i<cells;i++){
            int left=SpiritHudRules.cellX(100,i,false),right=SpiritHudRules.cellX(282,i,true);
            assertTrue(left>=100 && left+9<=181);
            assertTrue(right>=201 && right+9<=282);
            assertEquals(382,left+right+9,"life and energy fill from opposite outer edges");
            assertTrue(SpiritHudRules.cellY(baseline,i)>baseline-rows*10+8,"next armor/air row must not overlap");
        }
        if(cells>10){assertEquals(100,SpiritHudRules.cellX(100,10,false));assertEquals(190,SpiritHudRules.cellY(200,10));}
    }
}
