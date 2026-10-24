package cn.wwl.orimod.client;

import cn.wwl.orimod.client.render.GinsoWaterRenderType;
import cn.wwl.orimod.network.GinsoEscapePayload;
import cn.wwl.orimod.world.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Personal water exists only between owner start/end packets. Logout/world switches always erase it. */
public final class GinsoEscapeClient {
    private static GinsoEscapePayload state;
    private static ClientLevel world;private static double received;
    private static List<GinsoEscapeLayout.Box> boxes=List.of();
    private static List<GinsoFloodSurface.Rect> mesh=List.of();private static int band=Integer.MIN_VALUE;
    public static void receive(GinsoEscapePayload p){
        var mc=Minecraft.getInstance();boolean fresh=state==null||!state.session().equals(p.session());
        state=p;world=mc.level;received=clock();
        if(!p.flood().isEmpty()){boxes=p.flood();band=Integer.MIN_VALUE;}
        else if(fresh)boxes=List.of();
        if(!active()){boxes=List.of();mesh=List.of();return;}
        if(fresh&&mc.player!=null){
            mc.player.playSound(SoundEvents.CONDUIT_ACTIVATE,.7f,1.2f);
            var heart=new Vec3(p.origin().getX()+.5,p.origin().getY()+95.1,p.origin().getZ()+.5);
            for(int i=0;i<28;i++){double a=i*Math.PI*2/28;var at=heart.add(Math.cos(a)*.55,Math.sin(a*3)*.2,Math.sin(a)*.55);var velocity=mc.player.getEyePosition().subtract(at).scale(.09);
                mc.level.addParticle(ParticleTypes.END_ROD,at.x,at.y,at.z,velocity.x,velocity.y,velocity.z);}
        }
    }
    private static double clock(){var mc=Minecraft.getInstance();return mc.level==null?0:mc.level.getGameTime()+mc.getTimer().getGameTimeDeltaPartialTick(false);}
    public static boolean active(){var mc=Minecraft.getInstance();return state!=null&&mc.player!=null&&mc.level==world&&(state.phase()==GinsoEscape.Phase.PREPARE||state.phase()==GinsoEscape.Phase.RUNNING);}
    private static double age(){return state.elapsed()+Math.clamp(clock()-received,0,6);}
    private static double water(){return state.initialWater()+Math.max(0,age()-GinsoEscapeRules.PREPARE_TICKS)*GinsoEscapeRules.RISE_PER_SECOND/20;}
    public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(mc.level!=world||mc.player==null){state=null;world=mc.level;boxes=List.of();mesh=List.of();}}
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=null;world=null;boxes=List.of();mesh=List.of();}
    private static Vec3 world(double x,double y,double z){x-=.5;z-=.5;for(int i=0;i<state.turn();i++){double t=x;x=-z;z=t;}return new Vec3(state.origin().getX()+.5+x,state.origin().getY()+y,state.origin().getZ()+.5+z);}
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 camera,double x,double y,double z,float a){
        var p=world(x,y,z).subtract(camera);float glint=(float)(.5+.5*Math.sin(x*1.3+z*.75+clock()*.055));
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(.12f+glint*.08f,.58f+glint*.12f,.72f+glint*.12f,a);
    }
    public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!active())return;
        double y=water();int next=(int)Math.floor(y*2);
        if(next!=band){band=next;mesh=GinsoFloodSurface.at(boxes,y);}
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(GinsoWaterRenderType.WATER);
        var pose=new PoseStack().last();var camera=e.getCamera().getPosition();
        for(var r:mesh){
            // Small tiled facets provide a readable moving water surface without a full-screen glow quad.
            for(double x=r.x0();x<r.x1();x+=2)for(double z=r.z0();z<r.z1();z+=2){double x1=Math.min(x+2,r.x1()),z1=Math.min(z+2,r.z1());
                vertex(out,pose,camera,x,y,z,.42f);vertex(out,pose,camera,x,y,z1,.42f);vertex(out,pose,camera,x1,y,z1,.42f);vertex(out,pose,camera,x1,y,z,.42f);
            }
        }
        buffers.endBatch(GinsoWaterRenderType.WATER);
    }
    public static void register(RegisterGuiLayersEvent e){e.registerAboveAll(ResourceLocation.parse("orimod:ginso_escape"),(g,d)->{
        if(!active())return;var mc=Minecraft.getInstance();var camera=GinsoEscape.localPosition(state.origin(),state.turn(),mc.gameRenderer.getMainCamera().getPosition());
        if(camera.y<water()&&boxes.stream().anyMatch(b->b.contains(camera.x,camera.y,camera.z)))g.fill(0,0,g.guiWidth(),g.guiHeight(),0x3034A6C7);
        int remaining=(int)Math.ceil((state.limit()+GinsoEscapeRules.PREPARE_TICKS-age())/20);
        var text=age()<60?Component.translatable("hud.orimod.ginso.prepare",(int)Math.ceil((60-age())/20)):Component.translatable("hud.orimod.ginso.escape",remaining);
        g.drawCenteredString(mc.font,text,g.guiWidth()/2,38,0xB7F1F5);
        if(state.submerged()>0)g.drawCenteredString(mc.font,Component.translatable("hud.orimod.ginso.submerged",(60-state.submerged())/20f),g.guiWidth()/2,50,0xFFE5B3);
    });}
    public static Map<String,Object> diagnostics(){var m=new LinkedHashMap<String,Object>();m.put("active",active());m.put("phase",state==null?"READY":state.phase().name());m.put("floodBoxes",boxes.size());m.put("surfaceRects",mesh.size());if(state!=null){m.put("waterY",state.origin().getY()+water());m.put("elapsedTicks",age());m.put("session",state.session().toString());}return m;}
    private GinsoEscapeClient(){}
}
