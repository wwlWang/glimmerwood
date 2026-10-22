package cn.wwl.orimod.client;
/** Bounded layout even with modded maximum health/energy. Values remain the actual player values. */
public final class SpiritHudRules {
    private SpiritHudRules(){}
    public static float unit(float maximum,float base){return Math.max(base,(float)Math.ceil(maximum/(base*20))*base);}
    public static int cells(float maximum,float unit){return Math.clamp((int)Math.ceil(maximum/unit),0,20);}
    public static String fill(float value,float unit,int i){return value>=unit*(i+1)?"full":value>unit*i?"half":"empty";}
}
