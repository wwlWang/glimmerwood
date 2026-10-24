package cn.wwl.orimod.world;

import cn.wwl.orimod.movement.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.wwl.orimod.world.GinsoInterior.*;
import static cn.wwl.orimod.world.TallGinsoInterior.*;
import static org.junit.jupiter.api.Assertions.*;

class TallGinsoInteriorTest {
    private final GinsoCourses.Layout c=GinsoCourses.layout(2);
    @Test void packagedArtRecipeMatchesRealMinecraftCoordinateLayout()throws Exception{
        var path=java.nio.file.Path.of(System.getProperty("orimod.projectDir","."),"art/source/ginso_course/v002/layout.json");
        var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
        var cells=json.getAsJsonArray("cells");assertEquals(c.blocks().size(),cells.size());
        for(var e:cells){var p=e.getAsJsonArray();assertEquals(p.get(3).getAsByte(),c.cell(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()));}
    }
    private static boolean floor(byte b){return b==WOOD||b==LIGHT||b==CUSHION||b==WINDOW||b==WELL||b==STEP;}
    private static boolean blocked(byte b){return floor(b)||b==RAIL||b==DOOR||b==DOOR_TOP;}
    private boolean open(int x,int y,int z){return !blocked(c.cell(x,y,z))&&!blocked(c.cell(x,y+1,z));}
    @Test void savedVersionsHaveExactlyOneCourseAndLegacyStaysUntouched(){
        assertEquals(1,GinsoCourses.forShape(2));assertEquals(2,GinsoCourses.forShape(3));
        assertThrows(IllegalArgumentException.class,()->GinsoCourses.checked(3,1));
        assertThrows(IllegalArgumentException.class,()->GinsoCourses.checked(2,2));
        assertEquals(0,GinsoCourses.checked(2,0));
        assertEquals(GinsoInterior.layout().blocks(),GinsoCourses.layout(1).blocks());
    }
    @Test void threeWellsAreSeparatedAndHaveSafeBindingFeet(){
        assertEquals(List.of(21,51,94),c.wells().stream().map(BlockPos::getY).toList());
        for(var w:c.wells()){
            assertEquals(WELL,c.cell(w.getX(),w.getY(),w.getZ()));var p=w.north(2);
            assertTrue(floor(c.cell(p.getX(),p.getY()-1,p.getZ())));assertTrue(open(p.getX(),p.getY(),p.getZ()));
        }
        assertEquals(94,c.heart().getY());
    }
    @Test void shortHopsUseExistingAbilityLimitsAndEveryAnchorHasRealSupport(){
        for(var h:c.hops()){
            int rise=h.top()-h.floor();assertTrue(rise<=8,h.toString());
            var p=h.start();assertTrue(open(p.getX(),p.getY(),p.getZ()),h.toString());
            assertTrue(floor(c.cell(p.getX(),p.getY()-1,p.getZ())),h.toString());
            var goal=h.goal();assertTrue(open(goal.getX(),goal.getY(),goal.getZ()),h.toString());
            if(h.kind()==LAMP||h.kind()==FLOWER){
                var at=h.interaction();assertEquals(h.kind(),c.cell(at.getX(),at.getY(),at.getZ()));
                var support=h.kind()==FLOWER?at.north():at.offset(h.side(),0,0);
                assertEquals(WOOD,c.cell(support.getX(),support.getY(),support.getZ()));
                // Both normal human and compact spirit eye heights, with corner standing slack.
                for(double eye:new double[]{.85,1.62})for(double dx:new double[]{-.2,.2})for(double dz:new double[]{-.2,.2})
                    assertTrue(Math.sqrt(Math.pow(at.getX()-p.getX()-dx,2)+Math.pow(at.getY()+.5-p.getY()-eye,2)+Math.pow(at.getZ()-p.getZ()-dz,2))<=SpiritTargets.RANGE-.15,h.toString());
            }
            if(h.kind()==LAMP)assertTrue(rise+1.5<MovementRules.peak(1.25),"Fence clearance "+h);
            if(h.section().equals("jump"))assertTrue(rise<=2*MovementRules.peak(.42)+MovementRules.MANTLE_REACH-.4);
            if(h.kind()==WIND){var at=h.interaction();for(int y=at.getY()+1;y<=h.top()+2;y++)assertFalse(blocked(c.cell(at.getX(),y,at.getZ())),h.toString());}
        }
    }
    @Test void everyWalkableCourseEdgeHasLocalCatchAndClosedRetreatDoor(){
        var stats=new LinkedHashMap<String,int[]>();var failures=new ArrayList<String>();
        for(int y=5;y<=95;y++)for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
            if(!floor(c.cell(x,y-1,z))||!open(x,y,z))continue;
            var section=c.section(y);if(section==null)section=c.sections().getLast();
            var a=stats.computeIfAbsent(section.id(),k->new int[]{0,999,0});a[0]++;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0||!open(x+dx,y,z+dz))continue;
                // A diagonal may not cut the inside corner of either solid guard.
                if(dx!=0&&dz!=0&&(!open(x+dx,y,z)||!open(x,y,z+dz)))continue;
                int landing=y;while(landing>0&&!floor(c.cell(x+dx,landing-1,z+dz)))landing--;
                a[1]=Math.min(a[1],landing);a[2]=Math.max(a[2],y-landing);
                if(y-landing>10||landing<minimumLanding(section))failures.add(section.id()+" at "+new BlockPos(x,y,z)+" -> "+new BlockPos(x+dx,landing,z+dz));
            }
        }
        stats.forEach((s,a)->System.out.println("GIN_SO_FALL "+s+" standing="+a[0]+" lowest="+a[1]+" maxDrop="+a[2]));
        assertTrue(failures.isEmpty(),String.join("\n",failures.subList(0,Math.min(30,failures.size()))));
        for(var h:c.hops()){
            assertEquals(DOOR,c.cell(0,h.top()+1,-5));assertEquals(DOOR_TOP,c.cell(0,h.top()+2,-5));
            if(!h.section().equals("jump"))for(int z=-2;z<=2;z++)
                assertEquals(h.kind()==FLOWER&&(z==0||z==1)?AIR:h.top()==78&&z==0?DOOR:RAIL,c.cell(h.side(),h.top()+1,z));
        }
    }
    @Test void naturalHostilesAreRefusedAcrossEveryCourseFloorButOtherSpawnsRemainAvailable(){
        for(var p:c.blocks().keySet())if(floor(c.cell(p.getX(),p.getY(),p.getZ()))&&open(p.getX(),p.getY()+1,p.getZ())&&Math.abs(p.getX())<=4&&p.getZ()>=-8&&p.getZ()<=4&&p.getY()>=4&&p.getY()<95)
            assertTrue(GinsoSanctuary.refuses(2,p.above(),MobSpawnType.NATURAL,true),p.toString());
        for(var reason:MobSpawnType.values())if(reason!=MobSpawnType.NATURAL&&reason!=MobSpawnType.CHUNK_GENERATION)
            assertFalse(GinsoSanctuary.refuses(2,new BlockPos(0,30,0),reason,true));
        assertFalse(GinsoSanctuary.refuses(1,new BlockPos(0,30,0),MobSpawnType.NATURAL,true));
        assertFalse(GinsoSanctuary.refuses(2,new BlockPos(0,30,0),MobSpawnType.NATURAL,false));
    }
    @Test void railTopsLightKnotsAndVentLipAlsoHaveLocalCatches(){
        var stats=new LinkedHashMap<String,double[]>();
        for(var entry:c.blocks().entrySet()){
            var p=entry.getKey();byte b=entry.getValue();
            if(Math.abs(p.getX())>4||Math.abs(p.getZ())>4||p.getY()<4||p.getY()>94)continue;
            double height=height(b);if(height==0)continue;
            double feet=p.getY()+height;if(!bodyClear(p.getX(),feet,p.getZ()))continue;
            var section=c.section(feet);if(section==null)section=c.sections().getLast();
            // Arrival rails are the final standable surface of the incoming lesson.
            // They are recorded too, rather than silently excluding 1.5-high fence tops.
            for(var hop:c.hops())if(b==RAIL&&p.getY()==hop.top()+1&&!hop.section().equals("jump"))
                section=c.sections().stream().filter(s->s.id().equals(hop.section())).findFirst().orElseThrow();
            var a=stats.computeIfAbsent(section.id(),k->new double[]{0,999,0});a[0]++;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0||!bodyClear(p.getX()+dx,feet,p.getZ()+dz))continue;
                if(dx!=0&&dz!=0&&(!bodyClear(p.getX()+dx,feet,p.getZ())||!bodyClear(p.getX(),feet,p.getZ()+dz)))continue;
                double landing=0;
                for(int y=(int)Math.floor(feet);y>=0;y--){
                    double h=height(c.cell(p.getX()+dx,y,p.getZ()+dz));
                    if(h>0&&y+h<=feet+.00001){landing=y+h;break;}
                }
                a[1]=Math.min(a[1],landing);a[2]=Math.max(a[2],feet-landing);
                assertTrue(feet-landing<=10.00001&&landing>=minimumLanding(section),"Special surface "+p+" feet="+feet+" -> "+landing+" section="+section.id());
            }
        }
        stats.forEach((s,a)->System.out.println("GINSO_ALL_SURFACES "+s+" standing="+(int)a[0]+" lowest="+a[1]+" maxDrop="+a[2]));
    }
    private int minimumLanding(Section section){
        return section.id().equals("wind")?c.wells().stream().filter(w->w.getY()<=section.floor()+1).mapToInt(BlockPos::getY).max().orElseThrow():section.floor();
    }
    private static double height(byte b){return b==RAIL?1.5:b==WIND?.1875:b==STEP?.5:floor(b)||b==DOOR||b==DOOR_TOP?1:0;}
    private boolean bodyClear(int x,double feet,int z){
        for(int y=(int)Math.floor(feet)-1;y<feet+1.8;y++){
            double h=height(c.cell(x,y,z));if(h>0&&y+h>feet+.00001&&y<feet+1.8)return false;
        }return true;
    }
    @Test void retreatDropsAlternateAndCannotBeEnteredByAnAccidentalCourseStep(){
        var stops=c.retreat();
        for(int i=1;i<stops.size();i++){
            var hole=stops.get(i);var previous=stops.get(i-1);
            assertNotEquals(hole.getX(),previous.getX());
            assertEquals(CUSHION,c.cell(hole.getX(),previous.getY()-1,hole.getZ()));
            assertTrue(hole.getY()-previous.getY()<=8);
            assertEquals(DOOR,c.cell(0,hole.getY(),-5));
            assertEquals(DOOR_TOP,c.cell(0,hole.getY()+1,-5));
            assertEquals(CUSHION,c.cell(0,hole.getY()-1,-6));
            for(int z=-3;z>=-4;z--)assertTrue(bodyClear(0,hole.getY(),z),"Retreat door approach blocked at "+hole+" z="+z);
            // Closed wall, except the deliberately operated door, across the whole landing.
            for(int x=-4;x<=4;x++)assertTrue(blocked(c.cell(x,hole.getY(),-5)));
        }
    }
}
