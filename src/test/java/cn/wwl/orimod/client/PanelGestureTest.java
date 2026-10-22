package cn.wwl.orimod.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PanelGestureTest {
    @Test void tapOpensOnlyOnRelease(){var g=new PanelGesture();g.press(100);assertFalse(g.poll(349));assertTrue(g.release(349));assertFalse(g.release(349));}
    @Test void exactThresholdIsWheelOnly(){var g=new PanelGesture();g.press(0);assertTrue(g.poll(250));assertFalse(g.poll(300));assertFalse(g.release(300));}
    @Test void lowFpsLongPressNeverMisfiresPanel(){var g=new PanelGesture();g.press(0);assertFalse(g.release(400));}
    @Test void repeatsDoNotDelayWheel(){var g=new PanelGesture();g.press(0);g.press(200);assertTrue(g.poll(250));}
    @Test void focusLossAndScreenCloseForgetGesture(){var g=new PanelGesture();g.press(0);g.cancel();assertFalse(g.poll(300));assertFalse(g.release(301));g.press(400);assertTrue(g.release(450));}
}
