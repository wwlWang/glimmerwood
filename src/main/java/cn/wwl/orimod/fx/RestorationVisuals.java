package cn.wwl.orimod.fx;

/** World-unit choreography; independent of camera distance and rendering backend. */
public final class RestorationVisuals {
    public static final float COLUMN_HEIGHT=48, CORE_WIDTH=1.8f, HALO_WIDTH=3.6f;
    public static float columnFade(float height) {
        float t=Math.clamp(height/COLUMN_HEIGHT,0,1);
        return (float)Math.pow(1-t,.7);
    }
    public static float wispTravel(int index,float age) {
        float t=Math.clamp((age-(60+index*20))/35,0,1);
        return t*t*(3-2*t);
    }
    public static float wispAlpha(int index,float age) {
        return Math.clamp((age-10)/20,0,1)*Math.clamp((105+index*20-age)/10,0,1);
    }
    private RestorationVisuals(){}
}
