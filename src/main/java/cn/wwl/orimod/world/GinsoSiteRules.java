package cn.wwl.orimod.world;

/** Noise preflight rules shared with tests; shallow wetlands allowed, deep river channels refused. */
public final class GinsoSiteRules {
    public static final int SPACING=52, SEPARATION=20, SALT=11531023, MAX_RELIEF=8, MIN_LANDMARK_DISTANCE=300;
    public enum Reason { OK, BIOME, GREAT_TREE, BUILD_HEIGHT, RELIEF, DEEP_WATER, WET_FOOTPRINT, ROOT_DEEP_WATER, ROOT_CLIFF, GENERATED }
    public static Reason terrain(int low,int high,int wet,int total,int deepest){
        if(high-low>MAX_RELIEF)return Reason.RELIEF;
        if(deepest>2)return Reason.DEEP_WATER;
        if(total<=0 || wet*10>total*3)return Reason.WET_FOOTPRINT;
        return Reason.OK;
    }
    public static boolean awayFromGreatTree(int dx,int dz){return (long)dx*dx+(long)dz*dz>=(long)MIN_LANDMARK_DISTANCE*MIN_LANDMARK_DISTANCE;}
    /** The stilts follow smooth slopes; only deep channels and sharp/unsupported drops reject. */
    public static Reason rootSupport(int baseY,int floor,int waterSurface,int nearLow,int nearHigh){
        if(waterSurface-floor>2)return Reason.ROOT_DEEP_WATER;
        if(floor<baseY-16||floor>baseY+16||nearHigh-nearLow>8)return Reason.ROOT_CLIFF;
        return Reason.OK;
    }
    /** Actual v3 terminal-foot centres; same deterministic draws as TallGinsoShape, no voxel build. */
    public static int[][] rootFeet(long seed){
        var random=new java.util.Random(seed);random.nextInt(11);double phase=random.nextDouble()*.4;
        var out=new int[8][2];
        for(int i=0;i<8;i++){double a=i*Math.PI/4+.28+phase,r=22+random.nextDouble()*2;
            out[i][0]=(int)Math.round(Math.cos(a)*r);out[i][1]=(int)Math.round(Math.sin(a)*r);}
        return out;
    }
    private GinsoSiteRules(){}
}
