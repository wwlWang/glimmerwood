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

    @Test void capturedRightPalmPointsSwordForwardInsteadOfBackThroughFace() {
        // 02dcb0b in-game bone matrix (yaw=0). This catches the original duplicated hanging-arm pitch.
        var palm = new Matrix4f(.07486002f,.0012871337f,.031767294f,0, -.018060345f,.06860197f,.039779887f,0,
                -.026165657f,-.04366873f,.06342904f,0, 0,0,0,1);
        var direction = new Vector3f(1,1,0);
        palm.rotateX((float)Math.toRadians(SpiritItemGrip.attachmentPitch(1, 0))).rotateY((float)Math.PI)
                .rotateY(-(float)Math.PI / 2).rotateZ((float)Math.toRadians(55)).transformDirection(direction).normalize();
        assertTrue(direction.y > .5f && direction.z > .5f, "Sword should rise in front of the cradling hand");
        assertEquals(-90, SpiritItemGrip.attachmentPitch(0, 0), "Authored bow/attack calibration must stay intact");
        assertEquals(-45, SpiritItemGrip.attachmentPitch(.5f, 0), "Transition must blend with the actual cradle");
    }
}
