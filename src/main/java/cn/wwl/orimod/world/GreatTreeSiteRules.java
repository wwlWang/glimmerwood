package cn.wwl.orimod.world;

/** Terrain and light placement rules for the enlarged landmark, independent of chunk loading. */
public final class GreatTreeSiteRules {
    public static boolean column(int floor,int surface,int centre,int min,int max,boolean roots,boolean trunk,int sea){
        return rejection(floor,surface,centre,min,max,roots,trunk,sea)==null;
    }
    public static String rejection(int floor,int surface,int centre,int min,int max,boolean roots,boolean trunk,int sea){
        if(roots&&(floor<sea+1||surface-floor>1))return "ROOT_WATER";
        if(roots&&(max-min>10||Math.abs(floor-centre)>8))return "ROOT_RELIEF";
        return trunk&&Math.abs(floor-centre)>3?"TRUNK_RELIEF":null;
    }
    /** The overhanging ring may cross lakes and coasts; deep (>8-block) water cannot dominate it. */
    public static boolean canopy(int deepWater,int samples){return samples>0&&deepWater*5<=samples*3;}
    public static int searchLimit(long existingGameTime){return existingGameTime==0?800:2500;}
    public static boolean groundKnot(int x,int z){
        int d=x*x+z*z;
        return d>=20*20&&d<=78*78&&Math.floorMod(z,10)==0&&Math.floorMod(x+(Math.floorDiv(z,10)%2)*5,10)==0;
    }
    private GreatTreeSiteRules(){}
}
