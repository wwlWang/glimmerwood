package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritWeaponShapeTest {
    @Test void bowGripIsFixedWhileStringRetractsAtEachChargeThreshold() {
        var palm = SpiritWeaponShape.bowLimb(0);
        assertEquals(.28125f, palm.x); assertEquals(.71875f, palm.y);
        assertEquals(SpiritWeaponShape.bowString(0), SpiritWeaponShape.bowString(.649f));
        assertTrue(SpiritWeaponShape.bowString(.65f).distance(palm) > SpiritWeaponShape.bowString(0).distance(palm));
        assertEquals(SpiritWeaponShape.bowString(.65f), SpiritWeaponShape.bowString(.899f));
        assertTrue(SpiritWeaponShape.bowString(.9f).distance(palm) > SpiritWeaponShape.bowString(.65f).distance(palm));
        for (int i = 0; i <= 20; i++) {
            var p = SpiritWeaponShape.bowLimb(-1 + i / 10f);
            assertTrue(p.x >= 0 && p.x <= 1 && p.y >= 0 && p.y <= 1, "Limb clips the inventory icon");
        }
    }

    @Test void changingLayersCannotFlipTheItemInThePaw() {
        for(int i=0;i<=100;i++) for(float lowered:new float[]{0,.5f,1})
            assertEquals(-90,SpiritItemGrip.attachmentPitch(i/100f,lowered),"Socket calibration is fixed; pose owns the motion");
    }
}
