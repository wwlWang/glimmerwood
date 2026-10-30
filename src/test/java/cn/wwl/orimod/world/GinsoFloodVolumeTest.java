package cn.wwl.orimod.world;

import net.minecraft.core.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoFloodVolumeTest {
    @Test void adjacentCellsCullTheirSharedFaces() {
        var volume = GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,0,0,2,.5,1)));
        assertEquals(2, volume.cells().size());
        assertEquals(10, volume.faces(.5, c -> true).size());
        assertFalse(volume.faces(.5,c -> true).stream().anyMatch(f -> f.direction().getAxis() == Direction.Axis.X && f.x0()==1));
    }
    @Test void fractionalWaterClosesTopAndAllSidesAtTheSameHeight() {
        var volume = GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,0,0,1,2,1)));
        var faces = volume.faces(.23,c -> true);
        assertEquals(6, faces.size());
        assertEquals(.23, faces.stream().filter(f -> f.direction()==Direction.UP).findFirst().orElseThrow().y0());
        assertTrue(faces.stream().allMatch(f -> f.y0()>=0 && f.y1()<=.23));
    }
    @Test void clippingAPlayerWallRemovesWaterAndClosesTheNewBoundary() {
        var volume = GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,0,0,3,1,1)));
        var clipped = volume.clip(c -> c.x()!=1);
        assertFalse(clipped.contains(1.5,.25,.5)); assertTrue(clipped.contains(.5,.75,.5));
        assertEquals(20, clipped.faces(1,c -> true).size());
        assertEquals(clipped.cells(), GinsoFloodVolume.of(clipped.boxes()).cells());
    }
    @Test void overlappingRegionsCannotDoubleBlendWater() {
        var b = new GinsoEscapeLayout.Box(-1,2,-1,2,4,2);
        var single = GinsoFloodVolume.of(List.of(b)); var overlap = GinsoFloodVolume.of(List.of(b,b));
        assertEquals(single.cells(),overlap.cells()); assertEquals(single.faces(3,c -> true),overlap.faces(3,c -> true));
    }
    @Test void emptyOrDryVolumeHasNoFacesAndNoMembership() {
        var empty = GinsoFloodVolume.of(List.of()); assertFalse(empty.contains(0,0,0)); assertTrue(empty.boxes().isEmpty());
        var volume = GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,5,0,1,6,1)));
        assertTrue(volume.faces(5,c -> true).isEmpty()); assertFalse(volume.contains(0,6,0));
    }
    @Test void waterStopsAtTheEnclosingRimAndCannotFloatOverAnOpenCrown(){
        var volume=GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,0,0,3,4,3)));
        var enclosed=volume.enclosed(c->true,c->c.y()<2);
        assertEquals(36,enclosed.cells().size());
        assertTrue(enclosed.contains(1.5,1.99,1.5));assertFalse(enclosed.contains(1.5,2,1.5));
        assertEquals(enclosed.faces(2,c->true),enclosed.faces(100,c->true));
        assertTrue(enclosed.faces(100,c->true).stream().allMatch(f->f.y1()<=2));
    }
    @Test void OneOpenSideRejectsTheEntireConnectedHorizontalRegion(){
        var volume=GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,0,0,3,1,3)));
        var enclosed=volume.enclosed(c->true,c->!(c.x()==3&&c.z()==1));
        assertTrue(enclosed.cells().isEmpty());assertFalse(enclosed.contains(.5,.25,.5));
    }
    @Test void EnclosedChamberSurvivesAnAdjacentButDisconnectedOpenBranch(){
        var volume=GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,0,0,5,1,2)));
        var enclosed=volume.enclosed(c->c.x()!=2,c->c.x()<=2);
        assertTrue(enclosed.contains(.5,.25,.5));assertFalse(enclosed.contains(3.5,.25,.5));
        assertEquals(enclosed.cells(),GinsoFloodVolume.of(enclosed.boxes()).cells());
    }
    @Test void packetCellsHaveABoundedExpansionBudget() {
        assertThrows(IllegalArgumentException.class,() -> GinsoFloodVolume.of(List.of(new GinsoEscapeLayout.Box(0,0,0,128,160,128))));
    }
}
