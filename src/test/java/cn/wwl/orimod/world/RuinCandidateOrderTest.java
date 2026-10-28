package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RuinCandidateOrderTest {
    @Test void everyThreeKindFallbackCombinationMatchesFullSelection(){
        for(int relevantMask=0;relevantMask<8;relevantMask++)for(int viableMask=0;viableMask<8;viableMask++){
            boolean[] relevant=new boolean[3];for(int i=0;i<3;i++)relevant[i]=(relevantMask&(1<<i))!=0;
            int mask=viableMask,winner=RuinCandidateOrder.relevantWinner(relevant,i->(mask&(1<<i))!=0);
            int full=-1;for(int i=0;i<3;i++)if((mask&(1<<i))!=0){full=i;break;}
            assertEquals(full>=0&&relevant[full],winner>=0&&relevant[winner],"relevant="+relevantMask+" viable="+mask);
            if(winner>=0)assertEquals(full,winner,"A resolved winner must be the real weighted winner");
        }
    }
    @Test void viableNonIntersectingFirstChoicePreventsFalseFallback(){
        assertEquals(0,RuinCandidateOrder.relevantWinner(new boolean[]{false,true,false},i->true));
    }
    @Test void noRelevantCandidateOrAllRelevantFailuresSkipIrrelevantHeightQueries(){
        var calls=new AtomicInteger();
        assertEquals(-1,RuinCandidateOrder.relevantWinner(new boolean[3],i->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());
        assertEquals(-1,RuinCandidateOrder.relevantWinner(new boolean[]{false,true,false},i->{assertEquals(1,i);calls.incrementAndGet();return false;}));
        assertEquals(1,calls.get());
    }
}
