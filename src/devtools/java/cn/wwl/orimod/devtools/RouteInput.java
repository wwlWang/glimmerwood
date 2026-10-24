package cn.wwl.orimod.devtools;

import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Evidence controller: mouse yaw + short W pulses only. Never sets position, velocity, onGround or server state. */
final class RouteInput {
    private static List<Vec3> points=List.of();private static final List<Map<String,Object>> reached=new ArrayList<>();
    private static int index,ticks,stuck;private static Vec3 previous=Vec3.ZERO;private static String status="idle";
    static void start(JsonArray array){
        if(array.size()<2||array.size()>1024)throw new IllegalArgumentException("Route length");
        var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||!mc.isWindowActive())throw new IllegalStateException("Focused gameplay required");
        var p=new ArrayList<Vec3>();for(var v:array){var a=v.getAsJsonArray();var q=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
            if(!Double.isFinite(q.x+q.y+q.z)||q.distanceTo(mc.player.position())>160)throw new IllegalArgumentException("Route point");p.add(q);}
        stop();points=List.copyOf(p);index=0;ticks=0;stuck=0;previous=mc.player.position();reached.clear();status="walking";
    }
    static void stop(){Minecraft.getInstance().options.keyUp.setDown(false);if(status.equals("walking"))status="stopped";}
    static void tick(){
        if(!status.equals("walking"))return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||!mc.isWindowActive()){status="focus_lost";stop();return;}
        if(++ticks>2400){status="timeout";stop();return;}
        var pos=mc.player.position();
        while(index<points.size()&&pos.subtract(points.get(index)).horizontalDistance()<.22&&Math.abs(pos.y-points.get(index).y)<.65){
            reached.add(Map.of("index",index,"ticks",ticks,"position",List.of(pos.x,pos.y,pos.z)));index++;
        }
        if(index==points.size()){status="complete";stop();return;}
        var target=points.get(index);if(Math.abs(pos.y-target.y)>2){status="wrong_height";stop();return;}
        double dx=target.x-pos.x,dz=target.z-pos.z,d=Math.hypot(dx,dz);
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));mc.player.setYHeadRot(mc.player.getYRot());mc.player.setXRot(8);
        // Release every fourth tick. Near a node use one-tick taps, never a latched input.
        boolean press=d>.22&&(d>.5?ticks%4!=0:ticks%2==0);mc.options.keyUp.setDown(press);
        if(pos.distanceToSqr(previous)<1e-5)stuck++;else stuck=0;previous=pos;
        if(stuck>100){status="blocked";stop();}
    }
    static Map<String,Object> state(){return Map.of("status",status,"index",index,"total",points.size(),"ticks",ticks,"reached",List.copyOf(reached));}
    private RouteInput(){}
}
