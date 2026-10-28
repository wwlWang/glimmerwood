package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

class CreekTerrainRulesTest {
    @Test void savedLowerApproachIsAFixedBoundaryJustLikeTheTemplate(){
        var rules=new CreekTerrainRules(15,20,rectangle(15,20,(x,z)->9),7,2,15);
        IntBinaryOperator approach=(x,z)->z<0&&z>=-9&&x>=5&&x<=9?9:CreekTerrainRules.NONE;
        var plan=map(rules.plan(-8,-8,22,27,(x,z)->11,approach));
        var shoulder=plan.get(key(10,-3));
        assertTrue(Math.abs(shoulder.top()-approach.applyAsInt(9,-3))<=1,"Natural high shoulder overrides saved lower approach: "+shoulder);
        var sliced=new HashMap<Long,CreekTerrainRules.Column>();
        for(int z=-16;z<32;z+=16)for(int x=-16;x<32;x+=16)
            sliced.putAll(map(rules.plan(x,z,x+15,z+15,(a,b)->11,approach)));
        assertEquals(plan,sliced,"Saved entrance boundary depends on slice size");
    }
    @Test void highNaturalGroundCannotRaiseFirstRimColumnAboveAuthoredBank(){
        var rules=new CreekTerrainRules(15,20,rectangle(15,20,(x,z)->9),7,2,15);
        var plan=map(rules.plan(-8,-8,22,27,(x,z)->14,NO_APPROACH));
        for(var c:plan.values())for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            int x=c.x()+d[0],z=c.z()+d[1],authored=rules.ground(x,z);
            var neighbour=plan.get(key(x,z));
            int height=authored!=CreekTerrainRules.NONE?authored:neighbour==null?14:neighbour.top();
            assertTrue(Math.abs(c.top()-height)<=1,"Raised rim against fixed bank: "+c+" -> "+x+","+z+" y="+height);
        }
    }
    private static final IntBinaryOperator NO_APPROACH=(x,z)->CreekTerrainRules.NONE;
    private static List<CreekTerrainRules.Ground> rectangle(int sx,int sz,IntBinaryOperator height){
        var out=new ArrayList<CreekTerrainRules.Ground>();
        for(int z=0;z<sz;z++)for(int x=0;x<sx;x++)out.add(new CreekTerrainRules.Ground(x,z,height.applyAsInt(x,z)));
        return out;
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static Map<Long,CreekTerrainRules.Column> map(List<CreekTerrainRules.Column> columns){
        var out=new HashMap<Long,CreekTerrainRules.Column>();for(var c:columns)out.put(key(c.x(),c.z()),c);return out;
    }
    @Test void rearGradingHasNoExtraCliffsAndIsIdempotent(){
        var rules=new CreekTerrainRules(30,30,rectangle(30,30,(x,z)->18),15,2,20);
        var plan=map(rules.plan(-8,-8,37,37,(x,z)->10,NO_APPROACH));
        for(var c:plan.values())for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            var n=plan.get(key(c.x()+d[0],c.z()+d[1]));
            if(n!=null)assertTrue(Math.abs(c.top()-n.top())<=1,"Noisy grade created a trench");
        }
        // Writing a neighbouring slice must not amplify the procedural bumps.
        var after=map(rules.plan(-8,-8,37,37,(x,z)->{
            var c=plan.get(key(x,z));return c==null?10:c.top();
        },NO_APPROACH));
        for(var c:plan.values())assertEquals(c.top(),after.get(key(c.x(),c.z())).top());
    }
    @Test void packagedRearContourEveryStepVariesAndAdjacentStepsAreNotParallel() throws Exception {
        var path=Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/orimod/ruin_layout/creek_ruins_terrain_v1.json");
        var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var ground=new ArrayList<CreekTerrainRules.Ground>();var tops=new HashMap<Long,Integer>();
        for(var value:json.getAsJsonArray("columns")){
            var a=value.getAsJsonArray();var c=new CreekTerrainRules.Ground(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
            ground.add(c);tops.put(key(c.x(),c.z()),c.y());
        }
        var rules=new CreekTerrainRules(45,67,ground,22,2,50);
        for(var c:rules.plan(-8,-8,52,74,(x,z)->9,NO_APPROACH))tops.put(key(c.x(),c.z()),c.top());
        List<Integer> previous=null;
        for(int y=16;y>=10;y--){
            var edge=new ArrayList<Integer>();
            for(int x=12;x<=31;x++){
                int last=-1;
                for(int z=58;z<=74;z++)if(tops.getOrDefault(key(x,z),9)>=y)last=z;
                assertTrue(last>=58,"Missing rear terrace "+x+":"+y);edge.add(last);
            }
            assertTrue(new HashSet<>(edge).size()>=3,"Straight rear contour at "+y+": "+edge);
            for(int i=1;i<edge.size();i++)assertTrue(Math.abs(edge.get(i)-edge.get(i-1))<=1,"Abrupt edge waviness at "+y+": "+edge);
            if(previous!=null){
                var differences=new HashSet<Integer>();for(int i=0;i<edge.size();i++)differences.add(edge.get(i)-previous.get(i));
                assertTrue(differences.size()>1,"Parallel rear terraces at "+y+": "+edge);
            }
            previous=edge;
        }
    }
    @Test void upperBankUsesItsOwnEighteenBlockHeightNotTheFrontNine(){
        var g=rectangle(15,20,(x,z)->9+z*9/19);var rules=new CreekTerrainRules(15,20,g,7,2,15);
        var plan=map(rules.plan(-8,-8,22,27,(x,z)->9,NO_APPROACH));
        assertEquals(9,plan.get(key(7,-1)).top());
        assertEquals(17,plan.get(key(7,20)).top());
        assertEquals(14,plan.get(key(7,23)).top());
        assertEquals(10,plan.get(key(7,27)).top());
        assertFalse(plan.get(key(7,27)).retaining());
    }
    @Test void fourthToFifthAndEighthToNaturalHaveNoHiddenDrop(){
        var rules=new CreekTerrainRules(15,20,rectangle(15,20,(x,z)->9),7,2,15);
        var plan=map(rules.plan(-8,-8,22,27,(x,z)->2,NO_APPROACH));
        assertTrue(plan.get(key(7,23)).top()>=5&&plan.get(key(7,23)).top()<=7);
        assertTrue(plan.get(key(7,24)).top()>=4&&plan.get(key(7,24)).top()<=6);
        for(int z=20;z<27;z++)assertTrue(Math.abs(plan.get(key(7,z)).top()-plan.get(key(7,z+1)).top())<=1);
        assertTrue(Math.abs(plan.get(key(7,27)).top()-2)<=1);
        assertTrue(plan.values().stream().noneMatch(CreekTerrainRules.Column::retaining));
    }
    @Test void onlyTheActualRearCliffGetsRetainingEdges(){
        var g=rectangle(15,20,(x,z)->9+z*9/19);var rules=new CreekTerrainRules(15,20,g,7,2,15);
        var plan=rules.plan(-8,-8,22,27,(x,z)->9,NO_APPROACH);
        // An additional natural ravine behind the upper bank exceeds the eight-column reach.
        var deep=rules.plan(-8,-8,22,27,(x,z)->z>=20?1:9,NO_APPROACH);
        assertTrue(plan.stream().noneMatch(CreekTerrainRules.Column::retaining));
        assertTrue(deep.stream().anyMatch(c->c.rear()&&c.retaining()));
        assertTrue(deep.stream().filter(CreekTerrainRules.Column::retaining).allMatch(CreekTerrainRules.Column::rear));
        assertTrue(deep.stream().filter(CreekTerrainRules.Column::retaining).count()<deep.stream().filter(c->rules.outerBoundary(c.x(),c.z())).count()/2);
    }
    @Test void fiveWideApproachNeverGetsAGuardAndItsSavedFloorShapesTheShoulder(){
        var rules=new CreekTerrainRules(15,20,rectangle(15,20,(x,z)->18),7,2,15);
        var plan=rules.plan(-8,-8,22,27,(x,z)->1,(x,z)->z<0&&Math.abs(x-7)<=2?17:CreekTerrainRules.NONE);
        assertTrue(plan.stream().anyMatch(CreekTerrainRules.Column::retaining));
        assertTrue(plan.stream().filter(CreekTerrainRules.Column::entrance).noneMatch(CreekTerrainRules.Column::retaining));
        var p=map(plan);assertTrue(p.get(key(10,-5)).top()>=16,"Saved approach must support its adjacent bank");
    }
    @Test void enclosedCreekPocketIsNeverTreatedAsAnOuterRim(){
        var ground=rectangle(15,20,(x,z)->9);ground.removeIf(c->c.x()>=5&&c.x()<=9&&c.z()>=5&&c.z()<=14);
        var rules=new CreekTerrainRules(15,20,ground,7,2,15);
        var plan=rules.plan(-8,-8,22,27,(x,z)->2,NO_APPROACH);
        assertFalse(rules.rim(7,10));assertTrue(plan.stream().noneMatch(c->c.x()>=0&&c.x()<15&&c.z()>=0&&c.z()<20));
    }
    @Test void naturalSourcesSampledAtMostOncePerSlice(){
        var rules=new CreekTerrainRules(45,67,rectangle(45,67,(x,z)->9+z*9/66),22,2,50);
        var sampled=new HashSet<Long>();
        var plan=rules.plan(-8,0,7,15,(x,z)->{assertTrue(sampled.add(key(x,z)),"Repeated natural terrain query");return 9;},NO_APPROACH);
        assertFalse(plan.isEmpty());assertTrue(sampled.size()<16*40*4);
    }
    @Test void independentChunkSlicesMatchTheWholeGrade(){
        var rules=new CreekTerrainRules(45,67,rectangle(45,67,(x,z)->9+z*9/66),22,2,50);
        IntBinaryOperator natural=(x,z)->9+Math.floorDiv(x,16);
        var whole=map(rules.plan(-8,-8,52,74,natural,NO_APPROACH));var sliced=new HashMap<Long,CreekTerrainRules.Column>();
        for(int z=-16;z<80;z+=16)for(int x=-16;x<64;x+=16)sliced.putAll(map(rules.plan(x,z,x+15,z+15,natural,NO_APPROACH)));
        assertEquals(whole,sliced);
    }
    @Test void everyUnprotectedOuterTransitionFitsTheCliffRule(){
        var rules=new CreekTerrainRules(45,67,rectangle(45,67,(x,z)->9+z*9/66),22,2,50);
        IntBinaryOperator natural=(x,z)->z>67?3:9;
        var plan=map(rules.plan(-8,-8,52,74,natural,NO_APPROACH));
        for(var c:plan.values()){
            for(var d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){
                int x=c.x()+d[0],z=c.z()+d[1];
                if(rules.ground(x,z)!=CreekTerrainRules.NONE)continue;
                var neighbour=plan.get(key(x,z));
                int floor=neighbour==null?natural.applyAsInt(x,z):neighbour.top();
                if(c.top()-floor>1)assertTrue(c.retaining()||c.entrance(),"Unguarded bank "+c+" towards "+x+","+z);
            }
        }
    }
    @Test void duplicateAndOutOfFootprintCoordinatesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new CreekTerrainRules(3,3,List.of(new CreekTerrainRules.Ground(0,0,9),new CreekTerrainRules.Ground(0,0,10)),1,2,2));
        assertThrows(IllegalArgumentException.class,()->new CreekTerrainRules(3,3,List.of(new CreekTerrainRules.Ground(3,0,9)),1,2,2));
    }
    @Test void authoredRimHasNoLowRouteCutThroughRaisedBank() throws Exception {
        // The first real placement found a y=9 retreat touching a y=16 rear
        // bank at one exterior corner. No possible skirt can meet both. Check
        // the authored boundary itself before a costly four-rotation world run.
        var path=Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/orimod/ruin_layout/creek_ruins_terrain_v1.json");
        var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();var size=json.getAsJsonArray("size");
        var columns=new ArrayList<CreekTerrainRules.Ground>();
        for(var v:json.getAsJsonArray("columns")){
            var a=v.getAsJsonArray();columns.add(new CreekTerrainRules.Ground(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));
        }
        int sx=size.get(0).getAsInt(),sz=size.get(2).getAsInt();
        var entry=json.getAsJsonObject("entrance");
        var rules=new CreekTerrainRules(sx,sz,columns,entry.get("x").getAsInt(),entry.get("half_width").getAsInt(),json.get("rear_from_z").getAsInt());
        var plan=rules.plan(-8,-8,sx+7,sz+7,(x,z)->x<=3&&z>=48&&z<=61?0:z>=50?9:7,NO_APPROACH);
        for(var c:plan)for(var d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){
            int x=c.x()+d[0],z=c.z()+d[1],bank=rules.ground(x,z);
            if(bank==CreekTerrainRules.NONE)continue;
            assertTrue(Math.abs(c.top()-bank)<=1,"Incompatible bank/retreat: exterior "+c.x()+","+c.z()+" grade="+c.top()+" -> authored "+x+","+z+" y="+bank);
        }
    }
}
