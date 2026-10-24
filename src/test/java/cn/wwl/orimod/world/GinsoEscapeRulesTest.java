package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoEscapeRulesTest {
    @Test void pointwisePursuitAllowsTheSlowReferenceButNeverAnIdleHeartCamper(){
        for(int seed=0;seed<64;seed++)for(int turn=0;turn<4;turn++){
            var layout=GinsoEscapeLayoutTest.layout(seed,turn);
            var plan=GinsoEscapeRules.plan(layout);var samples=GinsoEscapeRules.samples(layout);
            for(var p:samples){
                assertTrue(p.feetMargin()>=5,"Feet caught: "+p);
                assertTrue(p.waterAtSpiritEyes()-p.referenceArrival()>=5,"Spirit caught: "+p);
                assertTrue(p.waterAtHumanEyes()-p.referenceArrival()>=5,"Human caught: "+p);
                if(p.index()>=samples.size()*2/3)assertTrue(p.waterAtHumanEyes()-p.referenceArrival()<=25,"No late threat seed="+seed+" turn="+turn+": "+p);
                if(p.index()<=samples.size()/3)assertTrue(p.waterAtHumanEyes()-p.referenceArrival()<=20,"No early threat: "+p);
            }
            assertTrue(GinsoEscapeRules.stationaryFailureSeconds(plan,GinsoEscapeRules.HUMAN_EYE)<=25);
            assertTrue(GinsoEscapeRules.stationaryFailureSeconds(plan,GinsoEscapeRules.SPIRIT_EYE)<=25);
            assertTrue(plan.limitTicks()/20.0-plan.referenceSeconds()>=16);
            // Quarter-block samples, using the old tread until the next block boundary.
            // Corner dwell is charged pessimistically during this interval as well.
            for(int i=1;i<layout.path().size();i++){
                var a=layout.path().get(i-1);var b=layout.path().get(i);
                for(int quarter=1;quarter<4;quarter++){
                    double f=quarter/4.0,feet=f<.5?a.feet():b.feet();
                    double time=3+GinsoEscapeRules.reference(a)+(GinsoEscapeRules.reference(b)-GinsoEscapeRules.reference(a))*f;
                    assertTrue(GinsoEscapeRules.reach(plan,feet)-time>=5,"Between-tread pursuit at "+i+"+"+f);
                }
            }
        }
    }
    @Test void waterIsConstantSpeedAfterWarningAndRetryHasNoPersistedWater(){
        var plan=GinsoEscapeRules.plan(GinsoEscapeLayoutTest.layout(0,0));
        assertEquals(plan.startWater(),GinsoEscapeRules.water(plan,0));
        assertEquals(plan.startWater(),GinsoEscapeRules.water(plan,60));
        assertEquals(.25,GinsoEscapeRules.water(plan,80)-GinsoEscapeRules.water(plan,60),1e-9);
        assertEquals(.25,GinsoEscapeRules.water(plan,800)-GinsoEscapeRules.water(plan,780),1e-9);
        assertEquals(plan.startWater(),GinsoEscapeRules.water(plan,0));
    }
}
