package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SpiritHandDiagnosticsTest {
    @Test
    void shippingRendererDefaultsToNoMeasurements() {
        assertFalse(SpiritHands.DIAGNOSTICS.enabled());
        assertFalse(SpiritHands.DIAGNOSTICS.isolateWeapon());
        var gate = new SpiritHandDiagnostics();
        for (int i = 0; i < 100; i++) gate.frameComplete();
        assertFalse(gate.enabled());
    }

    @Test
    void isolatedItemViewIsRecordingOnlyAndRestoresOnSaveOrDisconnect() {
        var gate=new SpiritHandDiagnostics();
        gate.isolateWeapon(true);assertFalse(gate.isolateWeapon());
        gate.recording(true);gate.isolateWeapon(true);assertTrue(gate.isolateWeapon());
        gate.recording(false);assertFalse(gate.isolateWeapon());
        gate.recording(true);gate.isolateWeapon(true);gate.reset();assertFalse(gate.isolateWeapon());
    }

    @Test
    void stateQueryExpiresAfterTwoFramesWithoutAnotherRequest() {
        var gate = new SpiritHandDiagnostics();
        gate.requestSample();
        assertTrue(gate.enabled());
        gate.frameComplete();
        assertTrue(gate.enabled());
        gate.frameComplete();
        assertFalse(gate.enabled());
    }

    @Test
    void poseRecordingOutlivesQueryLeaseAndStopsWhenSaved() {
        var gate = new SpiritHandDiagnostics();
        gate.requestSample();
        gate.recording(true);
        for (int i = 0; i < 100; i++) gate.frameComplete();
        assertTrue(gate.enabled());
        gate.recording(false);
        assertFalse(gate.enabled());
    }

    @Test
    void leavingWorldClearsBothRecordingAndQuery() {
        var gate = new SpiritHandDiagnostics();
        gate.recording(true);
        gate.requestSample();
        gate.reset();
        assertFalse(gate.enabled());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void disablingDiagnosticsDoesNotChangeTheBladeOrHandFit(boolean left) {
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.translate(left ? -.4f : .4f, -.3f, -.7f);
        pose.mulPose(new Matrix4f().rotateXYZ(.2f,-.3f,.4f));
        var before = new Matrix4f(pose.last().pose());
        try {
            for (boolean recording : new boolean[]{false,true}) {
                SpiritHands.DIAGNOSTICS.recording(recording);
                SpiritHands.beginHeld(null,null,null,pose,left);
                assertEquals(before,pose.last().pose());
                pose.popPose();
            }
        } finally { SpiritHands.DIAGNOSTICS.reset(); }
    }
}
