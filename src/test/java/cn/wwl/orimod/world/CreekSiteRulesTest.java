package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreekSiteRulesTest {
    @Test void coreKeepsFourWhileOuterAllowsEleven(){
        var allowed=CreekSiteRules.check((x,z)->x==44?81:x==32?74:70);
        assertTrue(allowed.allowed());assertEquals(74,allowed.coreMax());assertEquals(81,allowed.outerMax());
        assertFalse(CreekSiteRules.check((x,z)->x==32?75:70).allowed());
        assertFalse(CreekSiteRules.check((x,z)->x==44?82:70).allowed());
        assertFalse(RuinSiteRules.footprintOk(70,78),"Legacy relief must remain four");
    }
    @Test void thirteenSamplesAndEarlyReject(){
        int[] calls={0};assertTrue(CreekSiteRules.check((x,z)->{calls[0]++;return 70;}).allowed());assertEquals(13,calls[0]);
        calls[0]=0;assertFalse(CreekSiteRules.check((x,z)->{calls[0]++;return x==32?80:70;}).allowed());assertEquals(2,calls[0]);
    }
    @Test void waterAndBuildRangeStillRejected(){
        assertFalse(CreekSiteRules.check((x,z)->62).allowed());assertFalse(CreekSiteRules.check((x,z)->146).allowed());
    }
}
