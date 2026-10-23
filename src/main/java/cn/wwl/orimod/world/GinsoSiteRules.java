package cn.wwl.orimod.world;

/** Noise preflight rules shared with tests; shallow wetlands allowed, deep river channels refused. */
public final class GinsoSiteRules {
    public static final int SPACING=64, SEPARATION=24, SALT=11531023, MAX_RELIEF=8, MIN_LANDMARK_DISTANCE=300;
    public enum Reason { OK, BIOME, GREAT_TREE, BUILD_HEIGHT, RELIEF, DEEP_WATER, WET_FOOTPRINT }
    public static Reason terrain(int low,int high,int wet,int total,int deepest){
        if(high-low>MAX_RELIEF)return Reason.RELIEF;
        if(deepest>2)return Reason.DEEP_WATER;
        if(total<=0 || wet*10>total*3)return Reason.WET_FOOTPRINT;
        return Reason.OK;
    }
    public static boolean awayFromGreatTree(int dx,int dz){return (long)dx*dx+(long)dz*dz>=(long)MIN_LANDMARK_DISTANCE*MIN_LANDMARK_DISTANCE;}
    private GinsoSiteRules(){}
}
