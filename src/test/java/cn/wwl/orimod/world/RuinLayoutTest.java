package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RuinLayoutTest {
    @ParameterizedTest @EnumSource(RuinKind.class)
    void packagedLayoutHasUsableBoundsAndCheckpoints(RuinKind kind) {
        var layout = kind.layout();
        assertSame(kind, RuinKind.byId(kind.id));
        inside(Vec3.atLowerCornerOf(layout.shrine()), layout.size());
        inside(layout.safe(), layout.size()); inside(layout.entry(), layout.size());
        assertFalse(layout.checkpoints().isEmpty()); assertFalse(layout.critical().isEmpty());
        var actions = Set.of("jump", "wall_jump", "dash", "bash", "glide", "grapple", "stomp", "blade");
        for (var checkpoint : layout.checkpoints()) {
            inside(checkpoint.position(), layout.size()); assertTrue(actions.contains(checkpoint.action())); assertTrue(checkpoint.count() > 0);
        }
        for (var anchor : layout.critical()) inside(Vec3.atLowerCornerOf(anchor.position()), layout.size());
        for (var clearance : layout.clearances()) {
            inside(Vec3.atLowerCornerOf(clearance.from()), layout.size()); inside(Vec3.atLowerCornerOf(clearance.to()), layout.size());
            assertTrue(clearance.from().getX() <= clearance.to().getX());
            assertTrue(clearance.from().getY() <= clearance.to().getY());
            assertTrue(clearance.from().getZ() <= clearance.to().getZ());
        }
    }
    @Test void unknownLayoutFailsWithItsIdentifier() {
        var error = assertThrows(IllegalArgumentException.class, () -> RuinKind.byId("missing"));
        assertTrue(error.getMessage().contains("missing"));
    }
    private static void inside(Vec3 position, BlockPos size) {
        assertTrue(position.x >= 0 && position.y >= 0 && position.z >= 0);
        assertTrue(position.x < size.getX() && position.y < size.getY() && position.z < size.getZ(), position.toString());
    }
}
