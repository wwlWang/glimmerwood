package cn.wwl.orimod.client.render;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SpiritCarryPoseTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualRigCarryMatchesAttackEndpointsAndClearsTheGround(boolean left) throws Exception {
        var rig=new SpiritRigFixture();var carry=new SpiritPoseLayer("carry");carry.bind(rig.table);
        SpiritPoses.lowered(carry,left?1:0,0,1);carry.publish();var idle=rig.world(carry);
        var palm=rig.point(idle,left?"grip_r":"grip_l");var direction=rig.tip(idle,left);
        assertTrue(palm.y>.15 && palm.y<.45,"Palm below chest, above ground: "+palm);
        assertTrue(direction.z>.25 && direction.y>.25,"Weapon must point up/forward, keeping its head above the hanging paw: "+direction);
        assertTrue((left?1:-1)*direction.x>=.15f,"Weapon points away from the body, not behind the torso: "+direction);
        assertTrue(palm.y+direction.y*.55f>0,"Tip must clear the ground");
        for(float phase:new float[]{0,1}) {
            var attack=new SpiritPoseLayer("attack");attack.bind(rig.table);SpiritAttackPose.write(attack,phase,left,true,0,1);attack.publish();var world=rig.world(attack);
            assertEquals(0,palm.distance(rig.point(world,left?"grip_r":"grip_l")),1e-5,"No carry/attack socket jump");
            assertEquals(1,direction.dot(rig.tip(world,left)),1e-5,"No carry/attack item flip");
        }
    }
}
