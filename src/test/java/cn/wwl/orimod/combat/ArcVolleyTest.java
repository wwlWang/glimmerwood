package cn.wwl.orimod.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArcVolleyTest {
    @Test void onlyAFullSneakingChargeWithEnergy() {
        assertTrue(ArcVolley.applies(SpiritCombat.BOW_FULL_CHARGE, true, ArcVolley.COST));
        assertFalse(ArcVolley.applies(SpiritCombat.BOW_FULL_CHARGE - 1, true, 100));
        assertFalse(ArcVolley.applies(SpiritCombat.BOW_FULL_CHARGE, false, 100));
        assertFalse(ArcVolley.applies(SpiritCombat.BOW_FULL_CHARGE, true, ArcVolley.COST - 1));
    }

    @Test void fansThreeUnitArrowsSymmetrically() {
        var dirs = ArcVolley.directions(new Vec3(0, 0, 2));
        assertEquals(ArcVolley.ARROWS, dirs.size());
        for (var d : dirs) assertEquals(1, d.length(), 1e-5);
        assertEquals(1, dirs.get(0).z, 1e-9);
        assertEquals(-dirs.get(1).x, dirs.get(2).x, 1e-5);
        assertEquals(Math.cos(Math.toRadians(ArcVolley.SPREAD)), dirs.get(1).dot(dirs.get(0)), 1e-5);
    }
}
