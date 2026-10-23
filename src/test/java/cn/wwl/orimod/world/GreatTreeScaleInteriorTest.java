package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.wwl.orimod.world.GreatTreeInterior.*;
import static cn.wwl.orimod.world.GreatTreeScaleInterior.*;

class GreatTreeScaleInteriorTest {
    private static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
    private static Set<Point> decks(){
        var result=new HashSet<Point>();
        for(var route:List.of(GreatTreeInterior.layout().ascent(),GreatTreeScaleInterior.layout().upper())){
            int legLength=route.size()==72?6:12;
            for(int i=0;i<route.size();i++){
                var p=route.get(i);int leg=i/legLength%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
                result.add(p);result.add(p.add(-dz,0,dx));
            }
        }
        return result;
    }
    private static boolean solid(GreatTreeShape shape,int x,int y,int z){
        byte c=GreatTreeScaleInterior.layout().cell(x,y,z);
        if(c==NONE)c=shape.cell(x,y,z);
        return c!=NONE&&c!=AIR&&c!=WIND&&c!=GreatTreeShape.VINE;
    }
    @Test void everyTreadWidthHasNoAccessibleSideDropOverThreeBlocks(){
        var decks=decks();
        for(long seed:new long[]{115115,115116}){
            var shape=new GreatTreeShape(seed,2);
            for(var p:decks)for(var d:SIDES){
                var q=p.add(d[0],0,d[1]);
                if(solid(shape,q.x(),q.y(),q.z())||solid(shape,q.x(),q.y()+1,q.z()))continue;
                boolean supported=false;
                for(int drop=0;drop<=3;drop++)if(solid(shape,q.x(),q.y()-1-drop,q.z())){supported=true;break;}
                assertTrue(supported,"Unguarded drop >3 beside "+p+" toward "+q+" seed="+seed);
            }
        }
    }
    @Test void innerRailCoversCornersUnlessAnotherDeckOrHallProvidesTheExit(){
        var s=GreatTreeScaleInterior.layout();var route=s.upper();var decks=decks();
        for(int i=0;i<route.size();i++){
            var p=route.get(i);int leg=i/12%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            var q=p.add(-2*dz,0,2*dx);
            if(decks.stream().anyMatch(t->t.x()==q.x()&&t.z()==q.z()&&Math.abs(t.y()-q.y())<=1))continue;
            if(floor(s.cell(q.x(),q.y()-1,q.z())))continue; // level hall / landing, no open shaft
            assertTrue(s.cell(q.x(),q.y(),q.z())==RAIL||s.cell(q.x(),q.y()+1,q.z())==RAIL
                ||floor(s.cell(q.x(),q.y(),q.z())),"Open inner corner at step "+i+" "+q);
        }
    }
    private static boolean floor(byte c){return c==INNER||c==FINE_VEIN||c==KNOT||c>=NORTH&&c<=WEST;}
    @Test void bothStairsHaveRealTreadsHeadroomAndReversibleAdjacentSteps(){
        var s=GreatTreeScaleInterior.layout();
        for(var p:decks())for(int h=0;h<3;h++)
            assertEquals(AIR,s.cell(p.x(),p.y()+h,p.z()),"Full-width headroom "+p+" +"+h);
        for(var route:List.of(GreatTreeInterior.layout().ascent(),GreatTreeInterior.layout().descent(),s.upper())){
            Point last=null;
            for(var p:route){
                assertTrue(floor(s.cell(p.x(),p.y()-1,p.z())),"Tread "+p+" cell="+s.cell(p.x(),p.y()-1,p.z()));
                for(int y=p.y();y<p.y()+2;y++)assertEquals(AIR,s.cell(p.x(),y,p.z()),"Body "+p+" at "+y);
                if(last!=null){assertEquals(1,Math.abs(p.x()-last.x())+Math.abs(p.z()-last.z()));assertTrue(Math.abs(p.y()-last.y())<=1);}
                last=p;
            }
        }
        assertEquals(24,s.upper().getFirst().y());assertEquals(LOOKOUT_Y,s.upper().getLast().y());
        assertTrue(s.upper().size()>380&&s.upper().size()<600);
    }
    @Test void lowerAndUpperRoutesAreConnectedByWalkingNotByAbility(){
        var s=GreatTreeScaleInterior.layout();var start=ENTRY;
        var queue=new ArrayDeque<Point>();var seen=new HashSet<Point>();queue.add(start);seen.add(start);
        while(!queue.isEmpty()){
            var p=queue.remove();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy=-1;dy<=1;dy++){
                var q=p.add(d[0],dy,d[1]);byte f=s.cell(q.x(),q.y()-1,q.z());
                if(!floor(f)||s.cell(q.x(),q.y(),q.z())!=AIR||s.cell(q.x(),q.y()+1,q.z())!=AIR)continue;
                byte previous=s.cell(p.x(),p.y()-1,p.z());
                if(dy>0&&(f<NORTH||f>WEST)||dy<0&&(previous<NORTH||previous>WEST))continue;
                if(seen.add(q))queue.add(q);
            }
        }
        for(var p:s.upper())assertTrue(seen.contains(p),"Unreachable upper tread "+p);
        for(var p:List.of(new Point(2,24,-2),new Point(-1,-8,2),LOOKOUT))assertTrue(seen.contains(p),"Unreachable "+p);
    }
    @Test void stairsRemainLitAndWindIsContinuousButDoesNotReplaceTreads(){
        var s=GreatTreeScaleInterior.layout();var lamps=s.blocks().entrySet().stream().filter(e->e.getValue()==KNOT).map(Map.Entry::getKey).toList();
        for(var route:List.of(GreatTreeInterior.layout().ascent(),s.upper()))for(var p:route)
            assertTrue(lamps.stream().anyMatch(l->Math.abs(l.x()-p.x())+Math.abs(l.y()-p.y()-1)+Math.abs(l.z()-p.z())<=6),"Unlit "+p);
        for(int x=3;x<=5;x++)for(int z=2;z<=4;z++){
            for(int y=25;y<LOOKOUT_Y;y++)assertEquals(WIND,s.cell(x,y,z),"Wind gap "+new Point(x,y,z));
            assertEquals(VENT,s.cell(x,24,z));assertEquals(AIR,s.cell(x,LOOKOUT_Y,z));
        }
        for(var p:decks())for(var d:SIDES)assertNotEquals(WIND,s.cell(p.x()+d[0],p.y(),p.z()+d[1]),"Unguarded direct entry into wind from "+p);
        assertTrue(s.blocks().entrySet().stream().allMatch(e->e.getKey().y()<=GreatTreeShape.MAX_Y));
    }
    @Test void archAndVaultAreTallAndLegacyInteractionsRemainUnique(){
        var s=GreatTreeScaleInterior.layout();assertEquals(INNER,s.cell(0,15,-16));assertEquals(AIR,s.cell(0,14,-16));
        assertEquals(INNER,s.cell(7,11,-16));assertEquals(INNER,s.cell(2,43,2));
        assertEquals(HEART,s.cell(2,25,1));assertEquals(REWARD,s.cell(-1,-8,3));
        assertEquals(1,s.blocks().values().stream().filter(c->c==HEART).count());assertEquals(1,s.blocks().values().stream().filter(c->c==REWARD).count());
        assertTrue(s.blocks().values().stream().noneMatch(c->c==VEIN));
    }
    @Test void daylightReachesTheHallThroughGlazedLookoutFloor(){
        var s=GreatTreeScaleInterior.layout();
        for(int[] p:new int[][]{{1,1},{-1,-2},{5,-2}})for(int y=31;y<=MAX_Y;y++)
            assertEquals(y==LOOKOUT_Y-1?OCULUS:AIR,s.cell(p[0],y,p[1]),"Blocked sky at "+Arrays.toString(p)+" y="+y);
    }
}
