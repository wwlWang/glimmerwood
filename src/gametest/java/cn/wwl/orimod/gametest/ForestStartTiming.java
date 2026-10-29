package cn.wwl.orimod.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** B-only explanatory spans, including event-record overhead. Never enabled in CPU A/B acceptance. */
public final class ForestStartTiming {
    private static final ThreadLocal<State> ACTIVE=new ThreadLocal<>();
    private static final class State {
        int r2Depth;
        long forestInclusive,externalOwn,externalNeighbour,r2;
        final List<Map<String,Object>> predictions=new ArrayList<>(),ownQualifications=new ArrayList<>();
        Map<String,Object> prediction,lookup;
    }
    record Snapshot(Map<String,Double> phases,List<Map<String,Object>> predictions,List<Map<String,Object>> ownQualifications){}
    static void begin(){
        if(ACTIVE.get()!=null)throw new IllegalStateException("Nested start measurement");
        ACTIVE.set(new State());
    }
    public static boolean active(){return ACTIVE.get()!=null;}
    public static boolean ownForest(){var s=ACTIVE.get();return s!=null&&s.r2Depth==0;}
    public static boolean neighbour(){var s=ACTIVE.get();return s!=null&&s.r2Depth>0;}
    public static void forest(long elapsed){ACTIVE.get().forestInclusive+=elapsed;}
    public static long r2Nanos(){return ACTIVE.get().r2;}
    public static void external(long elapsed){
        var s=ACTIVE.get();if(s.r2Depth==0)s.externalOwn+=elapsed;else s.externalNeighbour+=elapsed;
    }
    public static void enterR2(){ACTIVE.get().r2Depth++;}
    public static void leaveR2(long elapsed){var s=ACTIVE.get();if(--s.r2Depth==0)s.r2+=elapsed;}
    public static void enterPrediction(int x,int z){
        var s=ACTIVE.get();if(s.prediction!=null)throw new IllegalStateException("Recursive neighbour prediction");
        s.prediction=new LinkedHashMap<>();s.prediction.put("chunk",List.of(x,z));
        s.prediction.put("qualifications",new ArrayList<Map<String,Object>>());s.prediction.put("terrainAttempts",new ArrayList<Map<String,Object>>());s.predictions.add(s.prediction);
    }
    @SuppressWarnings("unchecked") public static void neighbourTerrain(String kind,String result,long elapsed){
        var s=ACTIVE.get();if(s.prediction==null)throw new IllegalStateException("Terrain outside neighbour span");
        ((List<Map<String,Object>>)s.prediction.get("terrainAttempts")).add(Map.of("kind",kind,"result",result,"inclusiveWallMs",elapsed/1e6));
    }
    public static void leavePrediction(long elapsed,String kind){
        var s=ACTIVE.get();s.prediction.put("wallMs",elapsed/1e6);s.prediction.put("selected",kind);s.prediction=null;
    }
    @SuppressWarnings("unchecked") public static void enterQualification(Object key){
        var s=ACTIVE.get();if(s.lookup!=null)throw new IllegalStateException("Recursive external qualification");
        s.lookup=new LinkedHashMap<>();s.lookup.put("key",key.toString());s.lookup.put("computed",false);
        var target=s.prediction==null?s.ownQualifications:(List<Map<String,Object>>)s.prediction.get("qualifications");target.add(s.lookup);
    }
    public static void coldQualification(String set,int x,int z){
        var s=ACTIVE.get();if(s.lookup==null)throw new IllegalStateException("Qualification escaped cache observation");
        s.lookup.put("computed",true);s.lookup.put("set",set);s.lookup.put("chunk",List.of(x,z));
    }
    public static void leaveQualification(long elapsed,boolean valid){
        var s=ACTIVE.get();s.lookup.put("wallMs",elapsed/1e6);s.lookup.put("valid",valid);s.lookup=null;
    }
    static Snapshot finish(){
        var s=ACTIVE.get();ACTIVE.remove();
        if(s==null||s.r2Depth!=0||s.externalOwn>s.forestInclusive)throw new IllegalStateException("Incomplete start phases");
        var result=new LinkedHashMap<String,Double>();
        result.put("ownForestTerrainMs",(s.forestInclusive-s.externalOwn)/1e6);
        result.put("ownExternalQualificationMs",s.externalOwn/1e6);
        result.put("r2PredictionMs",s.r2/1e6);
        // Included in R2, not an extra additive phase.
        result.put("r2ExternalIncludedMs",s.externalNeighbour/1e6);
        return new Snapshot(result,List.copyOf(s.predictions),List.copyOf(s.ownQualifications));
    }
    private ForestStartTiming(){}
}
