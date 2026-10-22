package cn.wwl.orimod.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpiritHudRulesTest {
    @Test void cellsTrackRealHealthAndEnergy(){assertEquals(10,SpiritHudRules.cells(20,2));assertEquals(20,SpiritHudRules.cells(200,10));assertEquals("half",SpiritHudRules.fill(19,2,9));assertEquals("empty",SpiritHudRules.fill(0,2,0));assertEquals("full",SpiritHudRules.fill(20,2,9));}
    @Test void largeModdedStatsStayBounded(){for(float max:new float[]{40,200,1000,99999}){float unit=SpiritHudRules.unit(max,2);assertTrue(SpiritHudRules.cells(max,unit)<=20);assertTrue(unit*SpiritHudRules.cells(max,unit)>=max);}}
}
