package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForestSiteRulesTest {
    @Test void mergedCoreEntryFailureIsMonotoneWithoutChangingCoreMedian(){
        for(int lo=60;lo<=148;lo++)for(int hi=lo;hi<=148;hi++)
            for(int next:new int[]{60,63,lo,hi,145,148})
                if(!RuinSiteRules.footprintOk(lo,hi))
                    assertFalse(RuinSiteRules.footprintOk(Math.min(lo,next),Math.max(hi,next)));
        var rng=new Random(116118);
        for(int trial=0;trial<4000;trial++){
            int base=61+rng.nextInt(86),range=1+rng.nextInt(11);var values=new HashMap<PointKey,Integer>();
            for(var ps:List.of(ForestSiteRules.CORE,ForestSiteRules.OUTER))for(var p:ps)values.put(new PointKey(p.x(),p.z()),base+rng.nextInt(range));
            int[] entry=new int[5];for(int i=0;i<5;i++)entry[i]=base+rng.nextInt(range);
            java.util.function.IntBinaryOperator h=(x,z)->values.get(new PointKey(x,z));var old=ForestSiteRules.check(h);
            int min=Math.min(old.coreMin(),Arrays.stream(entry).min().orElseThrow()),max=Math.max(old.coreMax(),Arrays.stream(entry).max().orElseThrow());
            boolean allowed=old.allowed()&&RuinSiteRules.footprintOk(min,max);
            for(boolean owner:new boolean[]{false,true}){
                var fast=ForestSiteRules.checkWithEntry(h,dx->entry[dx+2],owner?()->false:null);
                assertEquals(allowed,fast.allowed(),"Changed decision at trial "+trial);
                if(allowed){assertEquals(old.anchor(),fast.anchor());assertEquals(old.coreMin(),fast.coreMin());assertEquals(old.coreMax(),fast.coreMax());}
            }
        }
    }
    private record PointKey(int x,int z){}
    @Test void ownerYieldSkipsOuterButPredictionNeverInvokesYield(){
        var visited=new HashSet<PointKey>();
        var own=ForestSiteRules.checkWithEntry((x,z)->{visited.add(new PointKey(x,z));return 70;},dx->70,()->true);
        assertEquals("R2_YIELD",own.firstReason());assertEquals(58,visited.size());
        visited.clear();var base=ForestSiteRules.checkWithEntry((x,z)->{visited.add(new PointKey(x,z));return 70;},dx->70,null);
        assertTrue(base.allowed());assertEquals(179,visited.size());
    }
    @Test void coreAndOuterLimitsRemainIndependent(){
        assertTrue(ForestSiteRules.inspect((x,z)->ForestSiteRules.core(x,z)?70:78).allowed());
        assertEquals(List.of("OUTER_RELIEF"),ForestSiteRules.inspect((x,z)->ForestSiteRules.core(x,z)?70:79).reasons());
        assertTrue(ForestSiteRules.inspect((x,z)->x==29?74:70).allowed());
        assertEquals(List.of("CORE_RELIEF"),ForestSiteRules.inspect((x,z)->x==29?75:70).reasons());
        assertFalse(RuinSiteRules.footprintOk(70,78),"Legacy stays at four");
    }
    @Test void fullDistributionDoesNotStopAtFirstInvalidCorner(){
        var points=new HashSet<ForestSiteRules.Point>();
        var r=ForestSiteRules.inspect((x,z)->{assertTrue(points.add(new ForestSiteRules.Point(x,z)));return x==0?62:x==38?146:70;});
        assertEquals(54,ForestSiteRules.CORE.size());assertEquals(125,ForestSiteRules.OUTER.size());assertEquals(179,points.size());
        assertEquals(62,r.footprintMin());assertEquals(146,r.footprintMax());
        assertEquals(List.of("HEIGHT_LOW","HEIGHT_HIGH","OUTER_RELIEF"),r.reasons());
    }
    @Test void medianComesFromCoreNotTallOuterHills(){
        var r=ForestSiteRules.inspect((x,z)->ForestSiteRules.core(x,z)?(x==29?74:70):78);
        assertEquals(70,r.anchor());assertEquals(78,r.footprintMax());assertTrue(r.allowed());
    }
    @Test void heightEndpointsAndCoreDomainAreExplicit(){
        assertTrue(ForestSiteRules.inspect((x,z)->63).allowed());assertTrue(ForestSiteRules.inspect((x,z)->145).allowed());
        assertEquals("HEIGHT_LOW",ForestSiteRules.inspect((x,z)->62).firstReason());
        assertEquals("HEIGHT_HIGH",ForestSiteRules.inspect((x,z)->146).firstReason());
        assertTrue(ForestSiteRules.CORE.contains(new ForestSiteRules.Point(29,38)));
        assertFalse(ForestSiteRules.core(30,38));
    }
    @Test void productionEarlyExitMatchesExhaustiveAcceptedSetAndMedian(){
        var random=new Random(116118);int accepted=0;
        for(int trial=0;trial<4000;trial++){
            int base=60+random.nextInt(90),coreRange=random.nextInt(7)+1,outerRange=random.nextInt(11)+1;
            var values=new HashMap<ForestSiteRules.Point,Integer>();
            for(var points:List.of(ForestSiteRules.CORE,ForestSiteRules.OUTER))for(var p:points)
                values.put(p,base+random.nextInt(ForestSiteRules.core(p.x(),p.z())?coreRange:outerRange));
            java.util.function.IntBinaryOperator height=(x,z)->values.get(new ForestSiteRules.Point(x,z));
            var all=ForestSiteRules.inspect(height);var fast=ForestSiteRules.check(height);
            assertEquals(all.allowed(),fast.allowed(),"Accepted-set drift "+trial);
            if(all.allowed()){accepted++;assertEquals(all.anchor(),fast.anchor());assertEquals(all.coreMin(),fast.coreMin());assertEquals(all.coreMax(),fast.coreMax());}
        }
        assertTrue(accepted>100);
    }
}
