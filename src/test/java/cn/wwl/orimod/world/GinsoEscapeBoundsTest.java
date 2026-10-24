package cn.wwl.orimod.world;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoEscapeBoundsTest {
    private static final List<GinsoEscapeLayout.Box> ROUTE=List.of(
            new GinsoEscapeLayout.Box(-1,99.5,-1,2,102.5,2));

    @Test void tripleJumpFitsButHorizontalEscapeAndDeepFallsStillFail(){
        assertTrue(GinsoEscape.inPlayableRoute(ROUTE,.5,106,.5));
        assertFalse(GinsoEscape.inPlayableRoute(ROUTE,.5,106.5,.5));
        assertTrue(GinsoEscape.inPlayableRoute(ROUTE,.5,98.5,.5));
        assertFalse(GinsoEscape.inPlayableRoute(ROUTE,.5,98.49,.5));
        for(double outside:new double[]{-1.01,2}){
            assertFalse(GinsoEscape.inPlayableRoute(ROUTE,outside,103,.5));
            assertFalse(GinsoEscape.inPlayableRoute(ROUTE,.5,103,outside));
        }
        assertFalse(GinsoEscape.inPlayableRoute(List.of(),.5,100,.5));
    }

    @Test void everyRouteTreadHasSixBlocksOfJumpRoomAndTheOldCourseRemainsOutside(){
        for(int turn=0;turn<4;turn++){
            var layout=GinsoEscapeLayoutTest.layout(115117,turn);
            for(var p:layout.path()){
                assertTrue(GinsoEscape.inPlayableRoute(layout.route(),p.x()+.5,p.feet()+6,p.z()+.5),"Jump at "+p);
                assertTrue(GinsoEscape.inPlayableRoute(layout.route(),p.x()+.5,p.feet()-.5,p.z()+.5),"Small drop at "+p);
            }
            assertFalse(GinsoEscape.inPlayableRoute(layout.route(),-2.5,91,1.5));
            assertFalse(GinsoEscape.inPlayableRoute(layout.route(),27.5,105,27.5));
        }
    }
}
