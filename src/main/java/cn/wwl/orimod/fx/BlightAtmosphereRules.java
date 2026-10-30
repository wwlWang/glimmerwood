package cn.wwl.orimod.fx;

/** No world access: envelope, outward purification wave, interpolation and send cadence. */
public final class BlightAtmosphereRules {
    public static final float MAX=.35f;
    public static final int LIMIT=4, INTERVAL=20, HEARTBEAT=100;
    public static float strength(double distance,float radius,boolean purifying,float progress){
        float edge=smooth((float)(radius-distance)/4);
        float wave=purifying?smooth((float)(distance-radius*progress)/4):1;
        return MAX*edge*wave;
    }
    private static float smooth(float x){x=Math.clamp(x,0,1);return x*x*(3-2*x);}
    public static float combined(float tide,float local,boolean vanillaPriority){return vanillaPriority?0:Math.max(tide,local);}
    public static boolean send(long age,boolean changed,boolean urgent){return urgent||age>=HEARTBEAT||changed&&age>=INTERVAL;}
    public static final class Fade {
        private float previous,current,target;
        public void tick(float goal){previous=current;target=Math.clamp(goal,0,MAX);float d=target-current;current+=Math.copySign(Math.min(Math.abs(d),MAX/20),d);}
        public float value(float partial){return previous+(current-previous)*Math.clamp(partial,0,1);}
        public float target(){return target;}
        public void clear(){previous=current=target=0;}
    }
    private BlightAtmosphereRules(){}
}
