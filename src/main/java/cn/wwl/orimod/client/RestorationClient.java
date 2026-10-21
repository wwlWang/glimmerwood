package cn.wwl.orimod.client;

import cn.wwl.orimod.client.render.SpiritEnergy;
import cn.wwl.orimod.fx.RestorationDisplay;
import cn.wwl.orimod.network.*;
import cn.wwl.orimod.progression.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import java.util.*;

/** A player-controlled view: layered light choreography, never a forced camera or modal Screen. */
public final class RestorationClient {
    private static final RestorationDisplay DISPLAY=new RestorationDisplay();
    private static RestorationPayload state=new RestorationPayload(false,false,Optional.empty(),BlockPos.ZERO,net.minecraft.world.phys.Vec3.ZERO,0,List.of());
    private static ClientLevel world;
    private static Vec3 origin=Vec3.ZERO;
    private static int cue=-1;
    public static void receive(RestorationPayload p){
        var mc=Minecraft.getInstance();boolean fresh=p.session().isPresent() && !p.session().get().equals(DISPLAY.session());
        state=p;DISPLAY.receive(p);world=mc.level;
        if(fresh && mc.player!=null){origin=p.origin();mc.player.setPos(origin);cue=-1;mc.player.stopUsingItem();}
    }
    public static boolean active(){var mc=Minecraft.getInstance();return mc.player!=null && mc.level==world && DISPLAY.active();}
    public static RestorationPayload state(){return state;}
    public static boolean escape(int key,int action){
        if(!active() || !DISPLAY.escape(Minecraft.getInstance().getConnection()!=null,key,action))return false;
        PacketDistributor.sendToServer(new ProgressActionPayload("restoration_skip",DISPLAY.session().toString().replace("-","")));return true;
    }
    public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level!=world){DISPLAY.clear();world=mc.level;return;}
        if(!mc.isPaused())DISPLAY.tick();if(!active())return;
        mc.player.setPos(origin);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.fallDistance=0;
        int second=(RestorationRules.DURATION-DISPLAY.remaining())/20;
        if(second!=cue){cue=second;
            if(second==0 || second==3 || second==4 || second==5 || second==13)
                mc.player.playSound(second==13?net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT:net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,.45f,second==13?.8f:1+second*.09f);
        }
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e){DISPLAY.clear();world=null;state=new RestorationPayload(false,false,Optional.empty(),BlockPos.ZERO,net.minecraft.world.phys.Vec3.ZERO,0,List.of());cue=-1;}
    public static Map<String,Object> diagnostics(){return Map.of("active",active(),"remaining",DISPLAY.remaining(),"restored",state.restored(),"completed",state.completed(),"session",state.session().map(UUID::toString).orElse(""),"second",(300-DISPLAY.remaining())/20);}
    private static float age(float partial){return 300-DISPLAY.remaining()+partial;}
    private static Vec3 heart(){return Vec3.atCenterOf(state.tree());}
    public static Vec3 companion(float partial){float t=age(partial);double angle=t*.055,r=t<120?1.4:3.5;
        var from=origin.add(0,1.4,0);var target=heart().add(Math.cos(angle)*r,1+Math.max(0,t-120)*.035,Math.sin(angle)*r);
        return from.lerp(target,Math.clamp(t/40,0,1));
    }
    private static Vec3 wisp(int i,float t){
        double angle=t*.05+i*Math.PI*2/3;var orbit=origin.add(Math.cos(angle)*.8,1.1+Math.sin(angle*.6)*.2,Math.sin(angle)*.8);
        float travel=Math.clamp((t-(60+i*20))/35,0,1);travel=travel*travel*(3-2*travel);
        return orbit.lerp(heart().add(Math.cos(angle)*.3,1.2,Math.sin(angle)*.3),travel);
    }
    public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active())return;
        var mc=Minecraft.getInstance();float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false),t=age(partial);
        // Match SpiritAura: LevelRenderer has already installed camera rotation.
        // Subtract in double precision before converting to render vertices (also safe far from origin).
        var pose=new com.mojang.blaze3d.vertex.PoseStack();var camera=e.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();var glow=buffers.getBuffer(SpiritEnergy.glow());var mat=pose.last();
        for(var w:WillWisp.values()){
            float a=Math.clamp((t-10)/20,0,1)*Math.clamp((260-t)/30,0,1);
            var at=wisp(w.ordinal(),t);SpiritEnergy.orb(glow,mat,at.subtract(camera).toVector3f(),.42f,w.color,.6f*a);
            SpiritEnergy.orb(glow,mat,at.subtract(camera).toVector3f(),.11f,0xF5FFFF,.9f*a);
            for(int j=1;j<=8;j++)SpiritEnergy.orb(glow,mat,wisp(w.ordinal(),t-j*1.5f).subtract(camera).toVector3f(),.16f,w.color,a*(1-j/9f)*.18f);
        }
        // Roots, then three rising crown bands (seconds 6–9); motes never obscure the player's view.
        for(int band=0;band<4;band++){
            float phase=(t-(120+band*20))/65;if(phase<0 || phase>1)continue;
            double radius=(band==0?2:4+band)*Math.sin(Math.PI*phase/2),y=band==0?.1:4+band*3;
            for(int i=0;i<48;i++){double a=i*Math.PI*2/48;var at=heart().add(Math.cos(a)*radius,y,Math.sin(a)*radius);
                SpiritEnergy.orb(glow,mat,at.subtract(camera).toVector3f(),.25f,0xC0F3FF,(1-phase)*.42f);}
        }
        if(t>=120)for(int j=1;j<=10;j++)SpiritEnergy.orb(glow,mat,companion(partial-j*2).subtract(camera).toVector3f(),.14f,0xE2F9FF,(1-j/11f)*.22f);
        float peak=Math.clamp((t-260)/20,0,1)*Math.clamp((300-t)/10,0,1);
        if(peak>0){SpiritEnergy.orb(glow,mat,heart().add(0,1,0).subtract(camera).toVector3f(),2,0xDEFFFF,peak*.35f);
            var beam=buffers.getBuffer(SpiritEnergy.streak());SpiritEnergy.beam(beam,mat,heart().subtract(camera).toVector3f(),heart().add(0,48,0).subtract(camera).toVector3f(),3*peak,new Vector3f(1,0,0),0xC7F2FF,peak*.65f);}
        buffers.endBatch(SpiritEnergy.glow());buffers.endBatch(SpiritEnergy.streak());
    }
    public static void register(RegisterGuiLayersEvent e){e.registerAboveAll(ResourceLocation.parse("orimod:restoration"),(g,d)->{
        if(!active())return;var mc=Minecraft.getInstance();float t=age(d.getGameTimeDeltaPartialTick(false));
        int alpha=(int)(Math.clamp((t-270)/12,0,1)*Math.clamp((300-t)/12,0,1)*28);
        if(alpha>0)g.fill(0,0,g.guiWidth(),g.guiHeight(),alpha<<24|0xCDEFFF);
        g.drawCenteredString(mc.font,Component.translatable("screen.orimod.restoration.skip"),g.guiWidth()/2,24,0xD6EEFF);
    });}
    private RestorationClient(){}
}
