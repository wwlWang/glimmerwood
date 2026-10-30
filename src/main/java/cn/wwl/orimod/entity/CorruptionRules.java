package cn.wwl.orimod.entity;

public final class CorruptionRules {
    public static final int CANDIDATES=64,CHANGES=4,NAVIGATIONS=2,ATTACK_INTERVAL=20;
    public static final double RANGE=12;
    public static int exposure(int old,int elapsed,boolean sameOwner,boolean eligible){return eligible?Math.min(24000,(sameOwner?old:0)+Math.clamp(elapsed,0,20)):0;}
    public static boolean attack(boolean peaceful,boolean baby,boolean leashed,boolean ridden,boolean targetEligible,double distanceSquared){
        return !peaceful&&!baby&&!leashed&&!ridden&&targetEligible&&distanceSquared<=RANGE*RANGE;
    }
    public static float drain(float energy,float perSecond){return Math.max(0,energy-Math.max(0,perSecond));}
    private CorruptionRules(){}
}
