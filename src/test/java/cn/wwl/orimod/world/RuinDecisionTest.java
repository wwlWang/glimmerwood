package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuinDecisionTest {
    @Test void provisionalForestXZDoesNotDependOnUnresolvedAnchor() {
        for(var rotation:Rotation.values()) {
            var zero=RuinPlacement.atMinimum(RuinKind.FOREST.layout(1),new BlockPos(-160,0,240),rotation);
            for(int y:new int[]{54,62,136}) {
                var actual=new RuinPlacement(zero.origin().atY(y),rotation);
                var expected=RuinDecision.boxes(RuinKind.FOREST,1,zero);
                var boxes=RuinDecision.boxes(RuinKind.FOREST,1,actual);
                for(int i=0;i<boxes.size();i++) {
                    var a=expected.get(i);var b=boxes.get(i);
                    assertEquals(List.of(a.minX(),a.minZ(),a.maxX(),a.maxZ()),List.of(b.minX(),b.minZ(),b.maxX(),b.maxZ()));
                }
            }
        }
    }
    @Test void publishedDecisionDoesNotExposeMutableInputsOrBoxes(){
        var heights=new ArrayList<>(Collections.nCopies(45,70));var surfaces=new HashMap<BlockPos,Integer>();surfaces.put(BlockPos.ZERO,70);
        var site=new RuinDecision(RuinKind.CREEK,1,new RuinPlacement(BlockPos.ZERO,Rotation.NONE),heights,surfaces);
        heights.set(0,-999);surfaces.clear();assertEquals(70,site.approach()[0]);assertEquals(70,site.surfaceHeights().get(BlockPos.ZERO));
        var copy=site.approach();copy[0]=-999;assertEquals(70,site.approach()[0]);
        var before=site.boxes();before.getFirst().mutable().move(999,999,999);assertEquals(before,site.boxes());
        assertThrows(UnsupportedOperationException.class,()->site.approachHeights().set(0,1));
        assertThrows(UnsupportedOperationException.class,()->site.surfaceHeights().clear());
    }
    @Test void everyVersionAndRotationUsesTheSavedApproachWidthAndSkirt(){
        for(var kind:RuinKind.values())for(int version=0;version<=1;version++)for(var rotation:Rotation.values()){
            var layout=kind.layout(version);var p=RuinPlacement.atMinimum(layout,new BlockPos(-160,32,240),rotation);
            var boxes=RuinDecision.boxes(kind,version,p);var template=p.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1));
            int rim=version>0&&kind!=RuinKind.CAVE?8:0;
            assertEquals(template.minX()-rim,boxes.getFirst().minX());assertEquals(template.maxZ()+rim,boxes.getFirst().maxZ());
            var door=RuinApproachPiece.doorway(kind,layout,p);int radius=version>0&&kind==RuinKind.CAVE?4:2;
            var expected=door.blocks(new BlockPos(-radius,-24,-9),new BlockPos(radius,64,-1));
            assertEquals(RuinDecision.Box.of(expected),boxes.get(1));
        }
    }
}
