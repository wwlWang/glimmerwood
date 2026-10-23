package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KeystoneDoorShapeTest {
    @Test void rotatedCaveDoorStaysInsideOriginalTwoByTwoOpening() {
        for(boolean z:List.of(false,true)) {
            var p=new BlockPos(-32,17,48);
            var s=KeystoneDoorShape.of(List.of(p.above(),p.offset(z?0:1,1,z?1:0),p,p.offset(z?0:1,0,z?1:0)),1);
            assertEquals(p,s.root());assertEquals(z,s.alongZ());assertEquals(2,s.width());assertEquals(2,s.height());
            assertEquals(4,s.bounds().getXsize()*s.bounds().getYsize()*s.bounds().getZsize());
        }
    }
    @Test void negativeCoordinatesAndSingleCellAreValid() {
        var p=new BlockPos(-1,-20,-1);var s=KeystoneDoorShape.of(List.of(p),0);
        assertEquals(p,s.root());assertEquals(1,s.width());assertEquals(-.5,s.bounds().getCenter().x);
    }
    @Test void openingCanShowBothKeysButUntrustedCountsStayBounded() {
        assertEquals(2,KeystoneDoorShape.of(List.of(BlockPos.ZERO),2).filled());
        assertEquals(0,KeystoneDoorShape.of(List.of(BlockPos.ZERO),-1).filled());
        assertEquals(2,KeystoneDoorShape.of(List.of(BlockPos.ZERO),90).filled());
    }
    @Test void emptyDoorDoesNotCreateAnUnboundedVisual() {assertThrows(IllegalArgumentException.class,()->KeystoneDoorShape.of(List.of(),0));}
}
