package cn.wwl.orimod.fx;

import java.util.Optional;

/** One opening gesture; any raw mouse delta permanently hands this session back to the player. */
public final class RestorationLookGuide {
    public static final int TICKS=20;
    public record Angles(float yaw,float pitch){}
    private Angles from=new Angles(0,0),to=from;
    private boolean active,cancelled;
    public void start(float yaw,float pitch,float targetYaw,float targetPitch,boolean firstPerson){
        from=new Angles(yaw,pitch);to=new Angles(yaw+wrap(targetYaw-yaw),Math.clamp(targetPitch,-90,90));
        active=firstPerson;cancelled=false;
    }
    public Optional<Angles> advance(double ticks,double mouseX,double mouseY,boolean firstPerson){
        if(!active)return Optional.empty();
        if(!firstPerson || mouseX!=0 || mouseY!=0){active=false;cancelled=true;return Optional.empty();}
        float t=(float)Math.clamp(ticks/TICKS,0,1),blend=t*t*(3-2*t);
        var result=new Angles(from.yaw+(to.yaw-from.yaw)*blend,from.pitch+(to.pitch-from.pitch)*blend);
        if(t==1)active=false;
        return Optional.of(result);
    }
    public void clear(){active=cancelled=false;}
    public boolean active(){return active;}
    public boolean cancelled(){return cancelled;}
    public Angles target(){return to;}
    private static float wrap(float value){return (value%360+540)%360-180;}
}
