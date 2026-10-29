package cn.wwl.orimod.world;

import java.util.*;
import java.util.function.IntBinaryOperator;

/** Versioned forest siting policy; exhaustive reference and identical early-exit production check. */
public final class ForestSiteRules {
    public static final int POLICY_VERSION=2, CORE_RELIEF=4, OUTER_RELIEF=8;
    public record Point(int x,int z) {}
    public static final List<Point> CORE=grid(9,29,8,38);
    public static final List<Point> OUTER=grid(0,38,0,54).stream().filter(p->!core(p.x,p.z)).toList();
    public record Result(int coreMin,int coreMax,int exteriorMin,int exteriorMax,
                         int footprintMin,int footprintMax,int anchor,List<String> reasons) {
        public Result { reasons=List.copyOf(reasons); }
        public boolean allowed(){return reasons.isEmpty();}
        public String firstReason(){return allowed()?"PASS":reasons.getFirst();}
    }
    public static boolean core(int x,int z){return x>=9&&x<=29&&z>=8&&z<=38;}
    private static List<Point> grid(int x0,int x1,int z0,int z1){
        var out=new LinkedHashSet<Point>();
        for(int x=x0;x<=x1+3;x+=4)for(int z=z0;z<=z1+3;z+=4)out.add(new Point(Math.min(x,x1),Math.min(z,z1)));
        return List.copyOf(out);
    }
    /** Exhaustive diagnostic reference: never truncate a height distribution on first failure. */
    public static Result inspect(IntBinaryOperator height){
        int[] ys=CORE.stream().mapToInt(p->height.applyAsInt(p.x,p.z)).sorted().toArray();
        int coreMin=ys[0],coreMax=ys[ys.length-1],lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;
        for(var p:OUTER){int y=height.applyAsInt(p.x,p.z);lo=Math.min(lo,y);hi=Math.max(hi,y);}
        int allMin=Math.min(coreMin,lo),allMax=Math.max(coreMax,hi);var reasons=new ArrayList<String>();
        if(allMin<RuinSiteRules.MIN_SURFACE)reasons.add("HEIGHT_LOW");
        if(allMax>RuinSiteRules.MAX_SURFACE)reasons.add("HEIGHT_HIGH");
        if(coreMax-coreMin>CORE_RELIEF)reasons.add("CORE_RELIEF");
        // Same envelope meaning as CreekSiteRules: the outer cap includes the core.
        if(allMax-allMin>OUTER_RELIEF)reasons.add("OUTER_RELIEF");
        // Integral upper median; deterministic for the 54-sample, even-sized core.
        return new Result(coreMin,coreMax,lo,hi,allMin,allMax,ys[ys.length/2],reasons);
    }
    /** Same accepted set/anchor as inspect; monotone failures avoid needless noise columns. */
    public static Result check(IntBinaryOperator height){
        return checkWithEntry(height,null,null);
    }
    /** Entry belongs only to the combined range, never to the core median. The
     * optional owner callback is a single-layer XZ check, not a base decision. */
    static Result checkWithEntry(IntBinaryOperator height,java.util.function.IntUnaryOperator entry,
                                 java.util.function.BooleanSupplier yieldBeforeOuter){
        int entryLo=Integer.MAX_VALUE,entryHi=Integer.MIN_VALUE;
        if(entry!=null)for(int dx=-2;dx<=2;dx++){
            int y=entry.applyAsInt(dx);entryLo=Math.min(entryLo,y);entryHi=Math.max(entryHi,y);
            if(!RuinSiteRules.footprintOk(entryLo,entryHi))
                return new Result(entryLo,entryHi,entryLo,entryHi,entryLo,entryHi,y,List.of("ENTRY_RELIEF"));
        }
        int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE,n=0;int[] core=new int[CORE.size()];
        for(var p:CORE){int y=height.applyAsInt(p.x,p.z);core[n++]=y;lo=Math.min(lo,y);hi=Math.max(hi,y);
            String reason=y<RuinSiteRules.MIN_SURFACE?"HEIGHT_LOW":y>RuinSiteRules.MAX_SURFACE?"HEIGHT_HIGH":hi-lo>CORE_RELIEF?"CORE_RELIEF":null;
            if(reason!=null)return new Result(lo,hi,lo,hi,lo,hi,y,List.of(reason));
            if(entry!=null&&!RuinSiteRules.footprintOk(Math.min(lo,entryLo),Math.max(hi,entryHi)))
                return new Result(lo,hi,lo,hi,lo,hi,y,List.of("ENTRY_RELIEF"));}
        int coreLo=lo,coreHi=hi;Arrays.sort(core);int anchor=core[core.length/2],outerLo=Integer.MAX_VALUE,outerHi=Integer.MIN_VALUE;
        if(yieldBeforeOuter!=null){
            int cornerLo=lo,cornerHi=hi;
            for(int x:new int[]{0,38})for(int z:new int[]{0,54}){
                int y=height.applyAsInt(x,z);cornerLo=Math.min(cornerLo,y);cornerHi=Math.max(cornerHi,y);
                String reason=y<RuinSiteRules.MIN_SURFACE?"HEIGHT_LOW":y>RuinSiteRules.MAX_SURFACE?"HEIGHT_HIGH":cornerHi-cornerLo>OUTER_RELIEF?"OUTER_RELIEF":null;
                if(reason!=null)return new Result(coreLo,coreHi,cornerLo,cornerHi,cornerLo,cornerHi,anchor,List.of(reason));
            }
            if(yieldBeforeOuter.getAsBoolean())return new Result(coreLo,coreHi,cornerLo,cornerHi,cornerLo,cornerHi,anchor,List.of("R2_YIELD"));
        }
        for(var p:OUTER){int y=height.applyAsInt(p.x,p.z);lo=Math.min(lo,y);hi=Math.max(hi,y);outerLo=Math.min(outerLo,y);outerHi=Math.max(outerHi,y);
            String reason=y<RuinSiteRules.MIN_SURFACE?"HEIGHT_LOW":y>RuinSiteRules.MAX_SURFACE?"HEIGHT_HIGH":hi-lo>OUTER_RELIEF?"OUTER_RELIEF":null;
            if(reason!=null)return new Result(coreLo,coreHi,outerLo,outerHi,lo,hi,anchor,List.of(reason));}
        return new Result(coreLo,coreHi,outerLo,outerHi,lo,hi,anchor,List.of());
    }
    private ForestSiteRules(){}
}
