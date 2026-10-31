package cn.wwl.orimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientSessionClockTest {
    @Test void respawnAndWorldReplacementNeedNoNullTick(){
        var c=new ClientSessionClock();var p=new Object();var w=new Object();assertTrue(c.observe(p,w));assertFalse(c.observe(p,w));
        assertTrue(c.observe(new Object(),w));assertTrue(c.observe(p,new Object()));
    }
    @Test void ticksAreIndependentOfWorldTimeAndLoginResets(){
        var c=new ClientSessionClock();for(int i=0;i<10819;i++)c.tick();long until=c.now()+8;
        c.clear();c.observe(new Object(),new Object());assertEquals(10819,c.now());
        for(int i=0;i<8;i++)c.tick();assertEquals(until,c.now());
    }
    @Test void disconnectAndReconnectSameIdentityStillReset(){
        var c=new ClientSessionClock();var p=new Object();var w=new Object();c.observe(p,w);c.clear();assertTrue(c.observe(p,w));
        assertTrue(c.observe(null,null));assertFalse(c.observe(null,null));
    }
}
