package cn.wwl.orimod.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SpiritArmRigTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void animatedGripIsNeverClampedOrTurnedToMeetTheForearm(boolean left) {
        var random=new java.util.Random(42);
        for(int i=0;i<1024;i++) {
            var palm=new Vector3f(random.nextFloat()*2-1,random.nextFloat()*2-1,-random.nextFloat()*2-.1f);
            var rotation=new Quaternionf().rotationXYZ(random.nextFloat()*6.28f,random.nextFloat()*6.28f,random.nextFloat()*6.28f);
            var frame=SpiritArmRig.follow(left,palm,rotation);
            assertEquals(0,palm.distance(frame.palm()),1e-6,"vanilla swing/equip must retain its entire path");
            assertEquals(1,Math.abs(rotation.dot(frame.rotation())),1e-5,"full item-relative palm rotation");
            assertEquals(SpiritArmRig.UPPER,frame.shoulder().distance(frame.elbow()),1e-5);
            assertEquals(SpiritArmRig.FOREARM,frame.elbow().distance(frame.wrist()),1e-5);
            assertTrue(frame.elbow().isFinite());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void wristSocketKeepsItsBindLengthAndRotation(boolean left) {
        var palm=new Vector3f(left?-.56f:.56f,-.3f,-.72f);
        var socket=new Vector3f(left?.0357f:-.0357f,-.09f,.0108f);
        for(int angle=-180;angle<=180;angle+=15) {
            var rotation=new Quaternionf().rotationXYZ(angle*.0174533f,.7f,-1.2f);
            var frame=SpiritArmRig.follow(left,palm,rotation,socket);
            assertEquals(socket.length()*SpiritArmRig.PAW_SCALE,frame.palm().distance(frame.wrist()),1e-6);
            var expected=new Vector3f(socket).mul(SpiritArmRig.PAW_SCALE).rotate(rotation).add(palm);
            assertEquals(0,expected.distance(frame.wrist()),1e-6);
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void shoulderCanYieldWithoutStretchingOrMovingTheGrip(boolean left) {
        var palm=new Vector3f(left?-1.4f:1.4f,.8f,-1.7f);
        var frame=SpiritArmRig.follow(left,palm,new Quaternionf());
        assertEquals(palm,frame.palm());
        assertEquals(SpiritArmRig.UPPER,frame.shoulder().distance(frame.elbow()),1e-5);
        assertEquals(SpiritArmRig.FOREARM,frame.elbow().distance(frame.wrist()),1e-5);
    }
}
