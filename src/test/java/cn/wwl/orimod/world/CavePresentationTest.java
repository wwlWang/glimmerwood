package cn.wwl.orimod.world;

import cn.wwl.orimod.block.SanctuaryPresentation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CavePresentationTest {
    // Conservative complete model extents, including the moving map disc; centres at their core blocks.
    static final AABB BASIN=new AABB(-1.5,0,-1.5,1.5,2.8,1.5), MAP=new AABB(-.7,0,-.7,.7,2.2,.7);
    @Test void newCaveHasWalkingSpaceBetweenFullSizeModels() {
        var layout=RuinKind.CAVE.layout();
        double distance=Math.sqrt(layout.shrine().distSqr(layout.mapStone()));
        assertTrue(distance-1.5-.7>=1.2,"Cave basin and map pillar overlap / no passage: "+distance);
    }
    @Test void legacyPairKeepsBothCoresButTheirModelsNeverOverlap() {
        for(var dir:Direction.Plane.HORIZONTAL) {
            var basin=SanctuaryPresentation.fit(dir,true).bounds(BASIN);
            var map=SanctuaryPresentation.fit(dir.getOpposite(),false).bounds(MAP).move(dir.getStepX(),0,dir.getStepZ());
            assertFalse(basin.intersects(map),"Legacy model intersection: "+dir);
            assertTrue(basin.contains(0,.3,0),"Shrine moved away from interaction core");
            assertTrue(map.contains(dir.getStepX(),.3,dir.getStepZ()),"Map moved away from interaction core");
        }
    }
}
