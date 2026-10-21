package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CrackedRuinTest {
    @ParameterizedTest @CsvSource({"WALL,CHARGE_FLAME,true","WALL,SMASH,true","WALL,LIGHT_BURST,true","WALL,BASHED_IMPACT,true","WALL,SPEAR,true","WALL,STOMP,false",
            "FLOOR,CHARGE_FLAME,false","FLOOR,SMASH,false","FLOOR,LIGHT_BURST,false","FLOOR,BASHED_IMPACT,false","FLOOR,SPEAR,false","FLOOR,STOMP,true"})
    void completeAbilityMatrix(CrackedRuin.Kind kind,CrackedRuin.Source source,boolean expected) { assertEquals(expected,CrackedRuin.breaks(kind,source)); }
    @Test void unrelatedBlocksAndMissingSourceNeverBreak() {
        assertFalse(CrackedRuin.breaks(null,CrackedRuin.Source.STOMP));assertFalse(CrackedRuin.breaks(CrackedRuin.Kind.WALL,null));
    }
    @Test void onlySameKindAndFaceNeighborsJoinTheSnapshot() {
        var blocks=new HashMap<BlockPos,CrackedRuin.Kind>();
        blocks.put(BlockPos.ZERO,CrackedRuin.Kind.WALL);blocks.put(new BlockPos(1,0,0),CrackedRuin.Kind.WALL);
        blocks.put(new BlockPos(2,1,0),CrackedRuin.Kind.WALL); // Edge, not face.
        blocks.put(new BlockPos(0,0,1),CrackedRuin.Kind.FLOOR);
        var plan=CrackedRuin.plan(BlockPos.ZERO,CrackedRuin.Kind.WALL,blocks::get,64);
        assertEquals(List.of(new CrackedRuin.Step(BlockPos.ZERO,0),new CrackedRuin.Step(new BlockPos(1,0,0),1)),plan);
        blocks.put(new BlockPos(2,0,0),CrackedRuin.Kind.WALL);assertEquals(2,plan.size(),"Snapshot adopted a later block");
    }
    @Test void radiusIncludesEightButNotNineAndDelayIsGeometric() {
        var blocks=new HashMap<BlockPos,CrackedRuin.Kind>();
        for(int x=0;x<=9;x++)blocks.put(new BlockPos(x,0,0),CrackedRuin.Kind.WALL);
        blocks.put(new BlockPos(1,1,0),CrackedRuin.Kind.WALL);
        var plan=CrackedRuin.plan(BlockPos.ZERO,CrackedRuin.Kind.WALL,blocks::get,64);
        assertEquals(10,plan.size());assertTrue(plan.contains(new CrackedRuin.Step(new BlockPos(8,0,0),8)));
        assertTrue(plan.contains(new CrackedRuin.Step(new BlockPos(1,1,0),2)));
        assertTrue(plan.stream().noneMatch(s->s.pos().equals(new BlockPos(9,0,0))));
    }
    @Test void limitIncludesRootAndCannotExceedSixtyFour() {
        for(int limit:new int[]{0,1,10,64,200}) {
            var plan=CrackedRuin.plan(BlockPos.ZERO,CrackedRuin.Kind.FLOOR,p->CrackedRuin.Kind.FLOOR,limit);
            assertEquals(Math.min(64,limit),plan.size());assertEquals(plan.size(),plan.stream().map(CrackedRuin.Step::pos).distinct().count());
        }
    }
    @ParameterizedTest @CsvSource({"0,0,0,true","1,0,0,true","1,0,1,true","3,0,0,false","0,1,0,false","0,-1,0,false"})
    void stompRequiresFloorDirectlyBelowIncludingOneBlockDiagonal(int x,int y,int z,boolean expected) {
        assertEquals(expected,CrackedRuin.stompFloor(new Vec3(.5,1,.5),new BlockPos(x,y,z)));
    }
    @Test void vesselAmountIsStableForPositionAndSpansTheSpecifiedRange() {
        var values=new HashSet<Integer>();
        for(int x=-100;x<100;x++) {
            var pos=new BlockPos(x,64,-x);int amount=SpiritLightVesselBlock.amount(103,pos);
            assertTrue(amount>=25 && amount<=40);assertEquals(amount,SpiritLightVesselBlock.amount(103,pos));values.add(amount);
        }
        assertEquals(16,values.size());
    }
}
