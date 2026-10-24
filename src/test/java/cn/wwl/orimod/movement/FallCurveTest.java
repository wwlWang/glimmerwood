package cn.wwl.orimod.movement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FallCurveTest {
    private static double next(double v) { return (FeelTuning.vertical(v,false,false)-.08)*.98; }
    @Test void ordinaryFallUsesConstantGravityAndTwentyBlocksPerSecondCap() {
        for (double v : new double[]{-.1,-.3,-.6,-.8}) assertEquals((v-.1)*.98,next(v),1e-9);
        double v=-.001; for(int i=0;i<100;i++)v=next(v);
        assertEquals(-1,v,1e-9);
    }
    @Test void externalFastEndSettlesWithoutAOneTickBrakeOrFurtherAcceleration() {
        double v=-1.6;
        assertEquals(-1.39,next(v),1e-9);
        for(int i=0;i<5;i++){double n=next(v);assertTrue(n>=v && n<=-1);v=n;}
        assertEquals(-1,v,1e-9);
        v=-3;for(int i=0;i<12;i++){double n=next(v);assertTrue(n>=v && n<=-1);v=n;}
        assertEquals(-1,v,1e-9);
    }
}
