package cn.wwl.orimod.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** R41: Spirit Well ordering and key parsing. */
class SpiritWellTest {
    private static final List<String> KEYS = List.of("discover/minecraft:overworld/10,64,10", "discover/minecraft:overworld/-5,70,3",
            "discover/minecraft:the_nether/1,2,3", "clear/minecraft:overworld/10,64,10", "best/minecraft:overworld/10,64,10");

    @Test void nextWrapsWithinTheDimension() {
        assertEquals("minecraft:overworld/10,64,10", SpiritWell.next(KEYS, "minecraft:overworld/-5,70,3"));
        assertEquals("minecraft:overworld/-5,70,3", SpiritWell.next(KEYS, "minecraft:overworld/10,64,10"));
        assertNull(SpiritWell.next(KEYS, "minecraft:the_nether/1,2,3"), "only one nether well");
        assertNull(SpiritWell.next(List.of(), "minecraft:overworld/0,0,0"));
    }

    @Test void positionsAndCounts() {
        assertEquals(new BlockPos(-5, 70, 3), SpiritWell.position("minecraft:overworld/-5,70,3"));
        assertNull(SpiritWell.position("minecraft:overworld/nonsense"));
        assertEquals(2, SpiritWell.count(KEYS, "minecraft:overworld"));
        assertEquals(1, SpiritWell.count(KEYS, "minecraft:the_nether"));
    }
    @Test void chooserListsOnlyValidDiscoveriesInThisDimension() {
        assertEquals(List.of(new BlockPos(-5,70,3),new BlockPos(10,64,10)),SpiritWell.discovered(KEYS,"minecraft:overworld"));
        assertTrue(SpiritWell.discovered(List.of("discover/minecraft:overworld/nonsense","discover/minecraft:overworld/1,2,3/extra"),"minecraft:overworld").isEmpty());
    }
}
