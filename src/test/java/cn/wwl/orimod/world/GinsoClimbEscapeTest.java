package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoClimbEscapeTest {
    @Test void newVersionKeepsBottomHeartAndAllMovementSections() {
        var course = GinsoCourses.layout(3);
        assertEquals(new BlockPos(0, 5, 0), course.heart());
        assertEquals(16, course.hops().size());
        assertEquals(java.util.Set.of("wall", "bash", "grapple", "wind", "jump"), course.hops().stream().map(GinsoCourses.Hop::section).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, GinsoCourses.checked(3, 3));
        assertThrows(IllegalArgumentException.class, () -> GinsoCourses.checked(2, 3));
        assertEquals(2, GinsoEscapeLayout.checked(3, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> GinsoEscapeLayout.checked(3, 2, 2));
    }
    @Test void requiredSkillsAreExplicitAndIndividuallyChecked() {
        int all = GinsoClimbEscape.REQUIRED.stream().mapToInt(a -> a.bit).reduce(0, (a,b) -> a|b);
        assertTrue(GinsoClimbEscape.missing(all).isEmpty());
        for (var ability : GinsoClimbEscape.REQUIRED) assertEquals(java.util.List.of(ability), GinsoClimbEscape.missing(all & ~ability.bit));
        assertTrue(GinsoClimbEscape.REQUIRED.contains(Ability.CLIMB));
    }
    @Test void savedOldCourseAndEscapeAreNeverReinterpreted() {
        var tag = new CompoundTag(); tag.putLong("Origin", BlockPos.ZERO.asLong()); tag.putLong("ShapeSeed", 115117);
        tag.putInt("ShapeVersion", 3); tag.putInt("CourseVersion", 2); tag.putInt("EscapeVersion", 1);
        tag.putInt("O", -1); tag.putIntArray("BB", new int[]{-35,-20,-35,35,124,35});
        var old = new GinsoPiece((c,t) -> null, tag);
        assertEquals(94, old.escapeHeart().getY()); assertEquals(1, old.escapeVersion());
        assertEquals(.25, GinsoEscapeRules.plan(old.escape(), old.escapeVersion()).risePerSecond());
        tag.putInt("CourseVersion", 3); tag.putInt("EscapeVersion", 2);
        var fresh = new GinsoPiece((c,t) -> null, tag);
        assertEquals(5, fresh.escapeHeart().getY()); assertEquals(5, fresh.escapeSafe().getY());
        var saved = new CompoundTag(); fresh.addAdditionalSaveData(null, saved);
        assertEquals(3, saved.getInt("CourseVersion")); assertEquals(2, saved.getInt("EscapeVersion"));
    }
    @Test void crownTransfersStayWithinGrappleReachAndUseMostOfTreeHeight() {
        for (long seed : new long[]{115117, 1387583691837622L}) for (int turn = 0; turn < 4; turn++) {
            var built = GinsoClimbEscape.forTree(seed, turn, new GinsoShape(seed, 3)); var layout = built.layout();
            assertTrue(layout.blocks().keySet().stream().allMatch(p -> p.getY()>=94), "Crown support intrudes into the incoming jump shaft");
            for(int y=94;y<98;y++)assertEquals(GinsoShape.AIR,layout.cell(0,y,0),"First grapple shaft crossed by later crown support");
            assertTrue(layout.exit().y0() - 5 >= 97);
            assertTrue(layout.path().getLast().distance() < 250, "Long walking helix returned");
            for (var hop : built.crownHops()) {
                double dx = hop.interaction().getX() - hop.start().getX(), dz = hop.interaction().getZ() - hop.start().getZ();
                double dy = hop.interaction().getY() + .5 - hop.start().getY() - 1.12;
                assertTrue(Math.sqrt(dx*dx+dy*dy+dz*dz) < 6, "Unreachable crown flower");
                assertEquals(GinsoInterior.FLOWER, layout.cell(hop.interaction().getX(), hop.interaction().getY(), hop.interaction().getZ()));
                assertEquals(TallGinsoInterior.CUSHION, layout.cell(hop.goal().getX(), hop.goal().getY()-1, hop.goal().getZ()));
            }
        }
    }
}
