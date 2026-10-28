package cn.wwl.orimod.world;

import java.util.List;
import java.util.function.IntBinaryOperator;

/** V1 only: a legacy-sized playable core, with a wider terrain-adapting skirt. */
final class CreekSiteRules {
    static final int CORE_RELIEF=4, OUTER_RELIEF=11;
    record Point(int x,int z) {}
    // Entry, first stream reach and ability take-off occupy the old 21 x 35 scale.
    static final List<Point> CORE=List.of(new Point(12,0),new Point(32,34),new Point(32,0),new Point(12,34),
            new Point(22,17),new Point(22,0),new Point(22,34),new Point(12,17),new Point(32,17));
    static final List<Point> OUTER=List.of(new Point(0,0),new Point(44,66),new Point(44,0),new Point(0,66));
    record Result(boolean allowed,int coreMin,int coreMax,int outerMin,int outerMax,String reason) {}
    static Result check(IntBinaryOperator height){
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(var p:CORE){int y=height.applyAsInt(p.x,p.z);min=Math.min(min,y);max=Math.max(max,y);
            if(!RuinSiteRules.surfaceInRange(y)||max-min>CORE_RELIEF)return new Result(false,min,max,min,max,"CORE_RELIEF");}
        int allMin=min,allMax=max;
        for(var p:OUTER){int y=height.applyAsInt(p.x,p.z);allMin=Math.min(allMin,y);allMax=Math.max(allMax,y);
            if(!RuinSiteRules.surfaceInRange(y)||allMax-allMin>OUTER_RELIEF)return new Result(false,min,max,allMin,allMax,"OUTER_RELIEF");}
        return new Result(true,min,max,allMin,allMax,"PASS");
    }
    private CreekSiteRules(){}
}
