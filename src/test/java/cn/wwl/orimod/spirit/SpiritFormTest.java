package cn.wwl.orimod.spirit;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class SpiritFormTest {
    @Test void standingKeepsWidthAndUsesSpiritEyeHeight() {
        var value = SpiritForm.dimensions(Pose.STANDING, EntityDimensions.scalable(.6f, 1.8f));
        assertEquals(.6f, value.width(), .00001); assertEquals(1.2f, value.height(), .00001); assertEquals(1.04f, value.eyeHeight(), .00001);
    }
    @Test void crouchingFitsOneBlockWithLowerEyes() {
        var value = SpiritForm.dimensions(Pose.CROUCHING, EntityDimensions.scalable(.6f, 1.5f));
        assertEquals(.6f, value.width(), .00001); assertEquals(1f, value.height(), .00001); assertEquals(.85f, value.eyeHeight(), .00001);
    }
    @ParameterizedTest @EnumSource(value = Pose.class, mode = EnumSource.Mode.EXCLUDE, names = {"STANDING", "CROUCHING"})
    void specialVanillaPosesRetainTheirDimensions(Pose pose) {
        var vanilla = EntityDimensions.scalable(.6f, .6f).withEyeHeight(.4f);
        assertSame(vanilla, SpiritForm.dimensions(pose, vanilla));
    }
}
