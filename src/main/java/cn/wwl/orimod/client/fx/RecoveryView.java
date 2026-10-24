package cn.wwl.orimod.client.fx;

/** Camera-distance scaling for the local first-person recovery particles only. */
public final class RecoveryView {
    private RecoveryView() {}
    public static float alpha(float strength,int settingPercent) { return strength*Math.clamp(settingPercent,0,100)/100f; }
    public static int count(int budget, double distance) {
        return Math.min(budget, (int)Math.floor(4*Math.clamp((distance-.2)/.7,0,1)));
    }
    public static float size(float authored, double distance) {
        return Math.min(authored,.035f)*(float)Math.clamp(distance/1.2,0,1);
    }
    /** Coordinates in camera space, below and to either side of the reticle. */
    public record Point(double side,double down,double forward) {}
    public static Point point(int index,double phase) {
        double a=phase+index*2.4;
        return new Point((index%2==0?1:-1)*(.32+.035*Math.cos(a)),.42+.025*Math.sin(a),.85);
    }
}
