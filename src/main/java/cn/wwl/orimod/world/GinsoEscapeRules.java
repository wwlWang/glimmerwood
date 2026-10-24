package cn.wwl.orimod.world;

import java.util.*;

/** Pure tuning and pointwise pursuit proof; no player/session code in geometry gate 1. */
public final class GinsoEscapeRules {
    public static final int PREPARE_TICKS=60, SUBMERGED_TICKS=60, RETRY_TICKS=100;
    public static final double RISE_PER_SECOND=.25, REFERENCE_SPEED=2.5, DECISION_SECONDS=8,
            APPROACH_SECONDS=1.5, MIN_MARGIN_SECONDS=6, SPIRIT_EYE=.85, HUMAN_EYE=1.62;
    public record Plan(double startWater,int limitTicks,double referenceSeconds) {}
    public record Sample(int index,double feet,double referenceArrival,double waterAtFeet,
                         double waterAtSpiritEyes,double waterAtHumanEyes,double feetMargin) {}
    public static double reference(GinsoEscapeLayout.Point p){return APPROACH_SECONDS+p.distance()/REFERENCE_SPEED+p.corners()*DECISION_SECONDS/6;}
    public static Plan plan(GinsoEscapeLayout.Layout layout){
        if(layout.path().isEmpty())throw new IllegalArgumentException("No escape route");
        double start=layout.path().stream().mapToDouble(p->p.feet()-RISE_PER_SECOND*(reference(p)+MIN_MARGIN_SECONDS)).min().orElseThrow();
        double reference=reference(layout.path().getLast());
        return new Plan(start,(int)Math.ceil((reference+16)*20),reference);
    }
    /** Seconds after the right click, including the three-second readable warning. */
    public static double reach(Plan plan,double y){return PREPARE_TICKS/20.0+Math.max(0,(y-plan.startWater())/RISE_PER_SECOND);}
    public static double water(Plan plan,long ticks){return plan.startWater()+Math.max(0,ticks-PREPARE_TICKS)*RISE_PER_SECOND/20;}
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
