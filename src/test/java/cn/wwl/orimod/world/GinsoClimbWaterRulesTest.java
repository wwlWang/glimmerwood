package cn.wwl.orimod.world;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoClimbWaterRulesTest {
    private static GinsoEscapeLayout.Layout layout(){return GinsoClimbEscape.forTree(1387583691837622L,2,new GinsoShape(1387583691837622L,3)).layout();}
    @Test void allMeasuredPointsAllowTheSlowestTimesThirteenTenthsPlusEightSeconds() throws IOException {
        var plan=GinsoEscapeRules.plan(layout(),2);
        double minimum=Double.POSITIVE_INFINITY,maximum=0;int points=0;
        // Frozen server-tick observations from successful actual-input runs 01 / 03 / 04,
        // not generated from the water formula. Failures and probes are deliberately excluded.
        try(var reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/r117/ginso-climb-times.csv"),StandardCharsets.UTF_8))){
            reader.readLine();String line;
            while((line=reader.readLine())!=null){var v=line.split(",");double height=Double.parseDouble(v[1]);
                double a=Double.parseDouble(v[2]),b=Double.parseDouble(v[3]),c=Double.parseDouble(v[4]);
                double arrival=GinsoEscapeRules.reach(plan,height);
                double human=arrival-Math.max(a,Math.max(b,c))*1.3,fast=arrival-Math.min(a,Math.min(b,c));
                assertTrue(human>=8,v[0]+" human margin "+human);
                assertTrue(fast<=56.1,v[0]+" scripted margin "+fast);
                minimum=Math.min(minimum,human);maximum=Math.max(maximum,fast);points++;
            }
        }
        assertEquals(43,points);assertEquals(8.1616666667,minimum,1e-7);assertEquals(56.0243589744,maximum,1e-7);
    }
    @Test void profileIsContinuousAtFixedHeightsAndReachIsItsInverse(){
        var plan=GinsoEscapeRules.plan(layout(),2);
        for(double height:new double[]{-4,5,56.999,57,57.001,79,94,106,110}){
            double tick=GinsoEscapeRules.reach(plan,height)*20;
            assertEquals(height,GinsoEscapeRules.water(plan,tick),1e-8);
            assertEquals(height,GinsoEscapeRules.water(2,plan.startWater(),1,tick),1e-8);
            if(height>-4)assertTrue(GinsoEscapeRules.water(plan,tick-.001)<height);
            assertTrue(GinsoEscapeRules.water(plan,tick+.001)>height);
        }
        assertEquals(-4,GinsoEscapeRules.water(plan,0));assertEquals(-4,GinsoEscapeRules.water(plan,60));
        assertEquals(-3.4,GinsoEscapeRules.water(plan,80),1e-9);
    }
    @Test void multiplierScalesAllBandsButNotPreparationAndTimeoutAllowsSlowerWater(){
        var normal=GinsoEscapeRules.plan(layout(),2);
        for(double multiplier:new double[]{.5,1,2}){
            var plan=GinsoEscapeRules.plan(layout(),2,multiplier);
            assertTrue(plan.limitTicks()<=12000);
            assertEquals(-4,GinsoEscapeRules.water(plan,60));
            for(double y:new double[]{5,57,79,94,106}){
                assertEquals(3+(GinsoEscapeRules.reach(normal,y)-3)/multiplier,GinsoEscapeRules.reach(plan,y),1e-8);
                assertEquals(y,GinsoEscapeRules.water(plan,GinsoEscapeRules.reach(plan,y)*20),1e-8);
            }
            assertTrue(plan.limitTicks()/20.0>GinsoEscapeRules.reach(plan,110)+3);
        }
        for(double bad:new double[]{.49,2.01,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->GinsoEscapeRules.plan(layout(),2,bad));
    }
    @Test void legacyPlanIgnoresTheNewServerMultiplier(){
        var legacy=GinsoEscapeLayoutTest.layout(0,0);var old=GinsoEscapeRules.plan(legacy);
        for(double multiplier:new double[]{.5,1,2}){
            var plan=GinsoEscapeRules.plan(legacy,1,multiplier);assertEquals(old,plan);
            assertEquals(.25,plan.risePerSecond());
            assertEquals(.25,GinsoEscapeRules.water(plan,600)-GinsoEscapeRules.water(plan,580),1e-9);
        }
    }
}
