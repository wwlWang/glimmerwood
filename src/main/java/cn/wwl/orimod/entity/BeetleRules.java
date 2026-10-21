package cn.wwl.orimod.entity;

import net.minecraft.world.phys.Vec3;

/** R105 geometry and clock, independent of entities/worlds. */
public final class BeetleRules {
    public static final int WINDUP=15, STUN=30, FLIP=60, IMMUNITY=40, COOLDOWN=60;
    public static final double SPEED=.6, DISTANCE=7, MIN_RANGE=3, MAX_RANGE=8;
    public enum State { IDLE, WINDUP, CHARGING, STUNNED, FLIPPED }
    public record Clock(State state,int left,int immunity,int cooldown) {
        public static Clock idle(){return new Clock(State.IDLE,0,0,0);}
        public Clock step(){
            int guard=Math.max(0,immunity-1),cool=Math.max(0,cooldown-1),remaining=Math.max(0,left-1);
            if(left>0 && remaining==0)return switch(state){
                case WINDUP -> new Clock(State.CHARGING,0,guard,cool);
                case FLIPPED -> new Clock(State.IDLE,0,IMMUNITY,cool);
                default -> new Clock(State.IDLE,0,guard,cool);
            };
            return new Clock(state,remaining,guard,cool);
        }
        public Clock with(State next,int ticks){return new Clock(next,ticks,immunity,cooldown);}
        public Clock stopped(boolean wall){return new Clock(wall?State.STUNNED:State.IDLE,wall?STUN:0,immunity,COOLDOWN);}
        public boolean canFlip(){return state!=State.FLIPPED && immunity==0;}
        public Clock flipped(){return canFlip()?new Clock(State.FLIPPED,FLIP,immunity,Math.max(cooldown,COOLDOWN)):this;}
    }
    /** Direction points from the beetle toward the incoming attack, not toward the projectile's owner.
     * Upper hemisphere within 45 degrees of vertical is unarmored; a zero/unknown source is also unarmored. */
    public static boolean front(float bodyYaw,Vec3 incoming){
        if(incoming==null || !Double.isFinite(incoming.lengthSqr()))return false;
        double horizontal=incoming.horizontalDistance();
        if(horizontal<1e-7 || incoming.y>=horizontal)return false;
        var forward=Vec3.directionFromRotation(0,bodyYaw);
        return (forward.x*incoming.x+forward.z*incoming.z)/horizontal>=.5-1e-7;
    }
    public static float damageScale(State state,boolean front){return state==State.FLIPPED?2:front?.2f:1;}
    public static boolean chargeRange(double distanceSquared){return distanceSquared>=MIN_RANGE*MIN_RANGE && distanceSquared<=MAX_RANGE*MAX_RANGE;}
    private BeetleRules(){}
}
