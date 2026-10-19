package cn.wwl.orimod.item;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** R49: Spirit Smash lands ahead of the player and is slower and heavier than the blade. */
class SpiritHammerTest {
    @Test void impactIsAheadAlongYaw() {
        var south = SpiritHammerItem.impactPoint(Vec3.ZERO, 0);
        assertEquals(SpiritHammerItem.REACH, south.z, 1e-9); assertEquals(0, south.x, 1e-9);
        var west = SpiritHammerItem.impactPoint(Vec3.ZERO, 90);
        assertEquals(-SpiritHammerItem.REACH, west.x, 1e-9);
    }
    @Test void heavyTuning() {
        assertTrue(SpiritHammerItem.WIND_UP_TICKS >= 10 && SpiritHammerItem.COOLDOWN_TICKS >= 20);
        assertTrue(SpiritHammerItem.DAMAGE >= 8);
    }
}
