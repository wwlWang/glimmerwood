package cn.wwl.orimod.config;

import org.junit.jupiter.api.Test;

import static cn.wwl.orimod.config.SpiritCameraSync.Action.*;
import static cn.wwl.orimod.config.SpiritCameraView.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpiritCameraSyncTest {
    @Test void steadyStateDoesNothing() { assertEquals(NONE, SpiritCameraSync.decide(THIRD_PERSON_BACK, THIRD_PERSON_BACK, THIRD_PERSON_BACK)); }
    @Test void f5IsSaved() { assertEquals(SAVE_CAMERA, SpiritCameraSync.decide(THIRD_PERSON_BACK, THIRD_PERSON_BACK, FIRST_PERSON)); }
    @Test void menuChoiceIsApplied() { assertEquals(APPLY_SETTING, SpiritCameraSync.decide(FIRST_PERSON, THIRD_PERSON_BACK, THIRD_PERSON_BACK)); }
    @Test void menuChoiceWinsOverSameTickF5() { assertEquals(APPLY_SETTING, SpiritCameraSync.decide(FIRST_PERSON, THIRD_PERSON_BACK, THIRD_PERSON_FRONT)); }
    @Test void menuChoiceAlreadyLiveIsNoop() { assertEquals(NONE, SpiritCameraSync.decide(FIRST_PERSON, THIRD_PERSON_BACK, FIRST_PERSON)); }
    @Test void firstTickInSpiritFormSavesF5Only() {
        assertEquals(NONE, SpiritCameraSync.decide(FIRST_PERSON, null, FIRST_PERSON));
        assertEquals(SAVE_CAMERA, SpiritCameraSync.decide(FIRST_PERSON, null, THIRD_PERSON_FRONT));
    }
}
