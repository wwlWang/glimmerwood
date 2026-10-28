package cn.wwl.orimod.world;

import java.util.function.IntPredicate;

/** Resolve only as much of the weighted failure order as a collision needs. */
final class RuinCandidateOrder {
    static int relevantWinner(boolean[] relevant,IntPredicate viable){
        for(int candidate=0;candidate<relevant.length;candidate++){
            if(!relevant[candidate]||!viable.test(candidate))continue;
            // Earlier relevant candidates already failed. Earlier non-relevant
            // candidates MUST still be checked: a viable one terminates fallback.
            for(int prior=0;prior<candidate;prior++)if(!relevant[prior]&&viable.test(prior))return prior;
            return candidate;
        }
        // No relevant candidate survives. The actual (non-intersecting) winner
        // may remain unknown; never cache this as "no structure".
        return -1;
    }
    private RuinCandidateOrder(){}
}
