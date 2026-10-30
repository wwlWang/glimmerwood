package cn.wwl.orimod.world;

import java.util.*;

/** Shared server/client water clock; legacy trees retain their original constant-speed rules. */
public final class GinsoEscapeRules {
    public static final int PREPARE_TICKS=60, SUBMERGED_TICKS=60, RETRY_TICKS=100;
    public static final double RISE_PER_SECOND=.25, REFERENCE_SPEED=2.5, DECISION_SECONDS=8,
            APPROACH_SECONDS=1.5, MIN_MARGIN_SECONDS=6, SPIRIT_EYE=.85, HUMAN_EYE=1.62;
    // Three measured runs: slowest arrival at every point × 1.3, then at least eight seconds
    // before water reaches the feet. See scripts/analyze-r117-ginso-timing.py and the timing fixture.
    public static final double CLIMB_START_WATER=-4, CLIMB_REFERENCE_SECONDS=184.21;
    public record Plan(double startWater,int limitTicks,double referenceSeconds,int version,double speedMultiplier) {
        public Plan(double startWater,int limitTicks,double referenceSeconds){this(startWater,limitTicks,referenceSeconds,1,1);}
        /** Initial rate; later Escape 2 bands are selected by water height, never by player position. */
        public double risePerSecond(){return rate(version,startWater)*speedMultiplier;}
    }
    private static double rate(int version,double height){
        if(version!=2)return RISE_PER_SECOND;
        return height<57?.60:height<79?.65:height<94?.40:.65;
    }
    private static double nextBand(double height){return height<57?57:height<79?79:height<94?94:Double.POSITIVE_INFINITY;}
    public static boolean validMultiplier(double value){return Double.isFinite(value)&&value>=.5&&value<=2;}

    public record Sample(int index,double feet,double referenceArrival,double waterAtFeet,
                         double waterAtSpiritEyes,double waterAtHumanEyes,double feetMargin) {}
    public static double reference(GinsoEscapeLayout.Point p){return APPROACH_SECONDS+p.distance()/REFERENCE_SPEED+p.corners()*DECISION_SECONDS/6;}
    public static Plan plan(GinsoEscapeLayout.Layout layout){
        if(layout.path().isEmpty())throw new IllegalArgumentException("No escape route");
        double start=layout.path().stream().mapToDouble(p->p.feet()-RISE_PER_SECOND*(reference(p)+MIN_MARGIN_SECONDS)).min().orElseThrow();
        double reference=reference(layout.path().getLast());
        return new Plan(start,(int)Math.ceil((reference+16)*20),reference);
    }
    public static Plan plan(GinsoEscapeLayout.Layout layout,int version){return plan(layout,version,1);}
    public static Plan plan(GinsoEscapeLayout.Layout layout,int version,double multiplier){
        if(version!=2)return plan(layout); // Config never changes a previously generated Escape 1 tree.
        if(!validMultiplier(multiplier))throw new IllegalArgumentException("Ginso water multiplier must be 0.5–2.0");
        var provisional=new Plan(CLIMB_START_WATER,0,CLIMB_REFERENCE_SECONDS,2,multiplier);
        double top=layout.path().stream().mapToDouble(GinsoEscapeLayout.Point::feet).max().orElseThrow();
        // Timeout remains after water has passed even the highest route point, also at 0.5×.
        int limit=(int)Math.ceil((reach(provisional,top+4)+SUBMERGED_TICKS/20.0+10)*20);
        return new Plan(CLIMB_START_WATER,limit,CLIMB_REFERENCE_SECONDS,2,multiplier);
    }
    /** Seconds after the right click, including the three-second readable warning. */
    public static double reach(Plan plan,double y){
        double at=plan.startWater(),seconds=PREPARE_TICKS/20.0;
        while(at<y){double next=plan.version()==2?Math.min(y,nextBand(at)):y;
            seconds+=(next-at)/(rate(plan.version(),at)*plan.speedMultiplier());at=next;}
        return seconds;
    }
    public static double water(Plan plan,double ticks){return water(plan.version(),plan.startWater(),plan.speedMultiplier(),ticks);}
    /** Packet interpolation and the server use this same piecewise integration. */
    public static double water(int version,double start,double multiplier,double ticks){
        double at=start,seconds=Math.max(0,ticks-PREPARE_TICKS)/20;
        while(seconds>0){double rate=rate(version,at)*(version==2?multiplier:1);
            double next=version==2?nextBand(at):Double.POSITIVE_INFINITY;
            double duration=(next-at)/rate;
            if(seconds<=duration)return at+seconds*rate;
            at=next;seconds-=duration;
        }return at;
    }
    public static List<Sample> samples(GinsoEscapeLayout.Layout layout){
        var plan=plan(layout);var out=new ArrayList<Sample>();
        for(int i=0;i<layout.path().size();i++){
            var p=layout.path().get(i);double arrival=PREPARE_TICKS/20.0+reference(p),feet=reach(plan,p.feet());
            out.add(new Sample(i,p.feet(),arrival,feet,reach(plan,p.feet()+SPIRIT_EYE),reach(plan,p.feet()+HUMAN_EYE),feet-arrival));
        }return List.copyOf(out);
    }
    public static double stationaryFailureSeconds(Plan plan,double eye){return reach(plan,94+eye)+SUBMERGED_TICKS/20.0;}
    private GinsoEscapeRules(){}
}
