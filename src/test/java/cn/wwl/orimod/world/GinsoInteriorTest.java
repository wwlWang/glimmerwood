package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoInteriorTest {
    @Test void abilityHeightsLeaveHorizontalLandingTime(){
        var bash=GinsoInterior.SECTIONS.get(1);var jump=GinsoInterior.SECTIONS.get(4);
        assertTrue(bash.top()-bash.floor()<=cn.wwl.orimod.movement.MovementRules.peak(1.25)-2);
        // Ori keeps vanilla jump strength; no Jump Boost/Triple Jump/Climb is needed on this ledge.
        assertTrue(jump.top()-jump.floor()<=2*cn.wwl.orimod.movement.MovementRules.peak(.42)+cn.wwl.orimod.movement.MovementRules.MANTLE_REACH-.4);
    }
    @Test void consecutiveOpeningsHaveSolidCatchFloorsAndClearLandings(){
        var layout=GinsoInterior.layout();
        for(var s:GinsoInterior.SECTIONS){
            for(int x=-4;x<=4;x++)for(int z=-1;z<=1;z++)if(GinsoInterior.opening(x,z,s.side())){
                assertNotEquals(GinsoInterior.AIR,layout.cell(x,s.floor(),z),s.id()+" drops through previous level");
                assertEquals(GinsoInterior.AIR,layout.cell(x,s.top(),z),s.id()+" blocked ascent hole");
            }
            var p=s.goal();assertEquals(GinsoInterior.WOOD,layout.cell(p.getX(),p.getY()-1,p.getZ()));
            assertEquals(GinsoInterior.AIR,layout.cell(p.getX(),p.getY(),p.getZ()));
            assertEquals(GinsoInterior.AIR,layout.cell(p.getX(),p.getY()+1,p.getZ()));
        }
    }
    @Test void everyCourseRotationIsBijectiveAndBounded(){
        for(int t=0;t<4;t++)for(var p:GinsoInterior.layout().blocks().keySet()){
            var q=GinsoInterior.rotate(p,t);assertEquals(p,GinsoInterior.rotate(q,-t));
            assertTrue(Math.abs(q.getX())<=11&&Math.abs(q.getZ())<=11&&q.getY()<=42);
        }
    }
    @Test void entryChoiceMinimizesUphillCutAndGradedPathNeverNeedsAnAbility(){
        assertEquals(1,GinsoInterior.entrance(70,new int[][]{{74,79,81},{70,70,69},{67,65,63},{72,72,71}}));
        for(int offset=-20;offset<=20;offset++)for(int z=-22;z< -5;z++)
            assertTrue(Math.abs(GinsoInterior.approachFloor(z,offset)-GinsoInterior.approachFloor(z+1,offset))<=1);
    }
    @Test void lockedCourseHintsDoNotConsumeTheLearnedLesson(){
        for(var s:GinsoInterior.SECTIONS){
            var h=CompanionHints.Hint.byWireId("ginso_"+s.id());assertNotNull(h);
            assertEquals(GinsoInterior.hint(s,0),CompanionHints.wireId(h,0));
            if(s.required()!=null){assertTrue(GinsoInterior.hint(s,0).endsWith("_locked"));
                assertEquals(h.id,CompanionHints.wireId(h,s.required().bit));}
        }
    }
    @Test void twoWellsHaveDryOpenCheckpointFeetAwayFromAscentHoles(){
        assertEquals(2,GinsoInterior.WELLS.size());var layout=GinsoInterior.layout();
        for(var well:GinsoInterior.WELLS){var p=well.north(2);
            assertEquals(GinsoInterior.WELL,layout.cell(well.getX(),well.getY(),well.getZ()));
            assertEquals(GinsoInterior.WOOD,layout.cell(p.getX(),p.getY()-1,p.getZ()));
            for(int y=0;y<2;y++)assertEquals(GinsoInterior.AIR,layout.cell(p.getX(),p.getY()+y,p.getZ()));
        }
    }
}
