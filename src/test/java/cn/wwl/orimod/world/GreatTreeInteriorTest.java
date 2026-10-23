package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GreatTreeInteriorTest {
    private static boolean floor(byte c){return c==GreatTreeInterior.WOOD||c==GreatTreeInterior.VEIN||c==GreatTreeInterior.INNER||c==GreatTreeInterior.FINE_VEIN||c==GreatTreeInterior.KNOT||c>=GreatTreeInterior.NORTH&&c<=GreatTreeInterior.WEST;}
    private static boolean stair(byte c){return c>=8&&c<=11;}
    @Test void everyTreadHasTwoBlockHeadroomAndAContinuousReversibleStair(){
        var s=GreatTreeInterior.layout();
        for(var route:List.of(s.ascent(),s.descent())){
            GreatTreeInterior.Point previous=null;
            for(var p:route){
                assertTrue(floor(s.cell(p.x(),p.y()-1,p.z())),"Missing tread "+p);
                assertEquals(GreatTreeInterior.AIR,s.cell(p.x(),p.y(),p.z()),"Feet "+p);
                assertEquals(GreatTreeInterior.AIR,s.cell(p.x(),p.y()+1,p.z()),"Head "+p);
                if(previous!=null){assertEquals(1,Math.abs(p.x()-previous.x())+Math.abs(p.z()-previous.z()),"Disconnected stair");assertTrue(Math.abs(p.y()-previous.y())<=1);}
                previous=p;
            }
        }
        assertEquals(24,s.ascent().getLast().y());assertEquals(-8,s.descent().getLast().y());
    }
    @Test void foyerHallAndCaveConnectWithoutAnyJumpOrAbilityGate(){
        var s=GreatTreeInterior.layout();
        // Discrete conservative walking graph: only full treads or vanilla stairs, two clear blocks.
        var seen=new HashSet<GreatTreeInterior.Point>();var queue=new ArrayDeque<GreatTreeInterior.Point>();
        var entry=new GreatTreeInterior.Point(0,0,-14);queue.add(entry);seen.add(entry);
        while(!queue.isEmpty()){
            var p=queue.remove();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy=-1;dy<=1;dy++){
                var n=p.add(d[0],dy,d[1]);
                if(!floor(s.cell(n.x(),n.y()-1,n.z()))||s.cell(n.x(),n.y(),n.z())!=GreatTreeInterior.AIR||s.cell(n.x(),n.y()+1,n.z())!=GreatTreeInterior.AIR)continue;
                if(dy>0 && !stair(s.cell(n.x(),n.y()-1,n.z())))continue;
                if(dy<0 && !stair(s.cell(p.x(),p.y()-1,p.z())))continue;
                if(seen.add(n))queue.add(n);
            }
        }
        assertTrue(seen.contains(s.ascent().getLast()),"Hall unreachable");
        assertTrue(seen.contains(s.descent().getLast()),"Cave unreachable");
        assertTrue(seen.contains(new GreatTreeInterior.Point(-1,-8,2)),"Reward unreachable");
        assertTrue(seen.contains(new GreatTreeInterior.Point(2,24,-2)),"Heart dais unreachable");
    }
    @Test void stairsHaveVisibleLightKnotsEverySixStepsAndThreeRealWindowsPerTurn(){
        var s=GreatTreeInterior.layout();
        var lights=s.blocks().entrySet().stream().filter(e->e.getValue()==GreatTreeInterior.KNOT).map(Map.Entry::getKey).toList();
        for(var p:s.ascent())assertTrue(lights.stream().anyMatch(l->Math.abs(l.x()-p.x())+Math.abs(l.y()-(p.y()+1))+Math.abs(l.z()-p.z())<=5),"Unlit tread "+p);
        for(int turn=0;turn<3;turn++){
            for(int z=-12;z<=-3;z++)assertEquals(GreatTreeInterior.AIR,s.cell(1,turn*8+3,z));
            for(int x=6;x<=12;x++)assertEquals(GreatTreeInterior.AIR,s.cell(x,turn*8+5,0));
            for(int z=5;z<=12;z++)assertEquals(GreatTreeInterior.AIR,s.cell(2,turn*8+7,z));
        }
        assertTrue(s.blocks().values().stream().noneMatch(c->c==GreatTreeInterior.VEIN),"Exterior lightning used inside");
        for(int y=36;y<=82;y++)assertEquals(GreatTreeInterior.AIR,s.cell(2,y,1),"Sky shaft blocked");
    }
    @Test void heartAndRewardAreUniqueAndLayoutStaysWithinSlicedBounds(){
        var s=GreatTreeInterior.layout();
        assertEquals(1,s.blocks().values().stream().filter(c->c==GreatTreeInterior.HEART).count());
        assertEquals(1,s.blocks().values().stream().filter(c->c==GreatTreeInterior.REWARD).count());
        assertTrue(s.blocks().entrySet().stream().allMatch(e->e.getKey().y()>=GreatTreeShape.MIN_Y&&e.getKey().y()<=GreatTreeShape.MAX_Y&&Math.abs(e.getKey().x())<40&&Math.abs(e.getKey().z())<40));
        assertEquals(GreatTreeInterior.AIR,s.cell(2,50,1),"Beam oculus blocked");
    }
}
