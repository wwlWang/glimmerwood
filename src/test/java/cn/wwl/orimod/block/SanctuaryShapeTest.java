package cn.wwl.orimod.block;

import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SanctuaryShapeTest {
    @Test void eightKindsHaveBoundedFootprintsAndAtMostFourHandWrittenBoxesPerCell(){
        for(var kind:SanctuaryKind.values())for(var facing:Direction.Plane.HORIZONTAL){
            var cells=SanctuaryShape.cells(kind,facing);assertEquals(kind==SanctuaryKind.MAP?3:27,cells.size());
            assertEquals(BlockPos.ZERO,cells.getFirst().offset());var seen=new HashSet<BlockPos>();
            for(var c:cells){assertTrue(seen.add(c.offset()));assertTrue(c.boxes().size()<=4,kind+" "+c.offset());
                for(var b:c.boxes())assertTrue(b.minX>=0&&b.minY>=0&&b.minZ>=0&&b.maxX<=1&&b.maxY<=1&&b.maxZ<=1,b.toString());
                assertEquals(c.offset(),SanctuaryShape.rotate(SanctuaryShape.unrotate(c.offset(),facing),facing));
            }
        }
    }
    @Test void lowStoneRimAndOpenApproachAreDistinctFromReservation(){
        var cells=SanctuaryShape.cells(SanctuaryKind.WELL,Direction.NORTH);
        assertTrue(hits(cells,new AABB(1.2,.4,.35,1.4,.7,.65)),"Real rim is solid");
        assertFalse(hits(cells,new AABB(.35,1.1,-.8,.65,2.7,-.4)),"Front air is open");
        assertFalse(SanctuaryShape.cell(SanctuaryKind.WELL,Direction.NORTH,new BlockPos(1,0,0)).collision().isEmpty());
    }
    @Test void floatingStoneDiscHasAThinPhysicalSurfaceWithoutAnAirWall(){
        var c=SanctuaryShape.cell(SanctuaryKind.MAP,Direction.NORTH,new BlockPos(0,2,0));
        assertFalse(c.collision().isEmpty(),"Stone chart disc must have a thin physical surface");assertFalse(c.outline().isEmpty());
        var cells=SanctuaryShape.cells(SanctuaryKind.MAP,Direction.NORTH);
        assertTrue(hits(cells,new AABB(.2,2.01,.2,.8,2.04,.8)),"Mean-height disc surface missing");
        assertFalse(hits(cells,new AABB(.2,2.25,.2,.8,2.95,.8)),"No invisible full-cell wall above the disc");
        assertTrue(SanctuaryShape.cell(SanctuaryKind.MAP,Direction.NORTH,new BlockPos(1,0,0)).outline().isEmpty());
    }
    @Test void heartRootRingKeepsItsHollowCentreAndPhysicalRim(){
        var cells=SanctuaryShape.cells(SanctuaryKind.HEART,Direction.NORTH);
        assertFalse(hits(cells,new AABB(.2,.14,.2,.8,.37,.8)),"Root ring centre must remain air, not a hidden plate");
        assertTrue(hits(cells,new AABB(.38,.15,-.36,.62,.35,-.25)),"Root ring itself must remain solid");
    }
    @Test void heartFloorCorrectionOnlyRequiresThreeLayers(){assertEquals(.062622093,SanctuaryKind.HEART.floorLift());assertEquals(0,SanctuaryKind.WELL.floorLift());}
    private static boolean hits(java.util.List<SanctuaryShape.Cell> cells,AABB box){return cells.stream().anyMatch(c->c.boxes().stream().anyMatch(b->b.move(c.offset()).intersects(box)));}
}
