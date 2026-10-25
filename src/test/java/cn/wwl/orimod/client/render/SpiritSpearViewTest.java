package cn.wwl.orimod.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritSpearViewTest {
    @Test void nearEyeBoltAndTrailDoNotCoverTheOwner() {
        for(float age:new float[]{0,1,2,3})assertEquals(0,SpiritSpearView.opacity(true,true,age,10));
        for(float distance:new float[]{0,.6f,1.1f,2})assertEquals(0,SpiritSpearView.opacity(true,true,10,distance));
        assertEquals(.5f,SpiritSpearView.opacity(true,true,3.5f,20));
        assertEquals(1,SpiritSpearView.opacity(true,true,4,3));
    }
    @Test void otherViewersAndThirdPersonKeepTheOriginalAppearance() {
        for(boolean own:new boolean[]{false,true})for(boolean first:new boolean[]{false,true}) {
            if(own&&first)continue;
            assertEquals(1,SpiritSpearView.opacity(own,first,0,0));
            assertEquals(1,SpiritSpearView.opacity(own,first,30,100));
        }
    }
}
