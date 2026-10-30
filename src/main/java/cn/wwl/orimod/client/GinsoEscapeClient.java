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
    private static List<GinsoFloodVolume.Face> mesh=List.of();private static int band=Integer.MIN_VALUE;
    private static GinsoFloodVolume volume=GinsoFloodVolume.of(List.of());
    private static List<GinsoFloodVolume.Cell> cells=List.of();
    private static final Map<GinsoFloodVolume.Cell,Boolean> clear=new HashMap<>();
    private static int watch;private static boolean dirty;
    public static void receive(GinsoEscapePayload p){
        var mc=Minecraft.getInstance();boolean fresh=state==null||!state.session().equals(p.session());
        state=p;world=mc.level;received=clock();
        if(!p.flood().isEmpty()){
            boxes=p.flood();band=Integer.MIN_VALUE;volume=GinsoFloodVolume.of(boxes);cells=List.copyOf(volume.cells());clear.clear();watch=0;
            for(var cell:cells)clear.put(cell,GinsoFloodVolume.passable(world,p.origin(),p.turn(),cell));
        }
        else if(fresh)resetVolume();
        if(!active()){resetVolume();return;}
        if(fresh&&mc.player!=null){
            mc.player.playSound(SoundEvents.CONDUIT_ACTIVATE,.7f,1.2f);
            var heart=new Vec3(p.origin().getX()+.5,p.origin().getY()+(p.layoutVersion()==2?6.1:95.1),p.origin().getZ()+.5);
            for(int i=0;i<28;i++){double a=i*Math.PI*2/28;var at=heart.add(Math.cos(a)*.55,Math.sin(a*3)*.2,Math.sin(a)*.55);var velocity=mc.player.getEyePosition().subtract(at).scale(.09);
                mc.level.addParticle(ParticleTypes.END_ROD,at.x,at.y,at.z,velocity.x,velocity.y,velocity.z);}
        }
    }
    private static double clock(){var mc=Minecraft.getInstance();return mc.level==null?0:mc.level.getGameTime()+mc.getTimer().getGameTimeDeltaPartialTick(false);}
    public static boolean active(){var mc=Minecraft.getInstance();return state!=null&&mc.player!=null&&mc.level==world&&(state.phase()==GinsoEscape.Phase.PREPARE||state.phase()==GinsoEscape.Phase.RUNNING);}
    private static double age(){return state.elapsed()+Math.clamp(clock()-received,0,6);}
    private static double water(){return GinsoEscapeRules.water(state.layoutVersion(),state.initialWater(),state.speedMultiplier(),age());}
    private static void resetVolume(){boxes=List.of();mesh=List.of();volume=GinsoFloodVolume.of(List.of());cells=List.of();clear.clear();watch=0;band=Integer.MIN_VALUE;}
    public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.level!=world||mc.player==null){state=null;world=mc.level;resetVolume();return;}
        if(!active()||cells.isEmpty())return;
        // Bounded polling catches placed/removed blocks without chunk-load requests or a renderer mixin.
        for(int i=0;i<Math.min(256,cells.size());i++){
            var cell=cells.get(watch++%cells.size());boolean value=GinsoFloodVolume.passable(world,state.origin(),state.turn(),cell);
            if(!Objects.equals(clear.put(cell,value),value))dirty=true;
        }
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=null;world=null;resetVolume();}
    private static Vec3 world(double x,double y,double z){x-=.5;z-=.5;for(int i=0;i<state.turn();i++){double t=x;x=-z;z=t;}return new Vec3(state.origin().getX()+.5+x,state.origin().getY()+y,state.origin().getZ()+.5+z);}
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 camera,double x,double y,double z,
                               float u,float v,int tint,int light,Vec3 normal){
        var p=world(x,Math.min(y,water()),z).subtract(camera);
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor((tint>>16)&255,(tint>>8)&255,tint&255,180)
                .setUv(u,v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
    public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!active())return;
        double y=water();int next=(int)Math.floor(y*2);
        if(next!=band||dirty){band=next;dirty=false;mesh=volume.faces((next+1)*.5,c->clear.getOrDefault(c,false));}
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(GinsoWaterRenderType.WATER);
        var sprite=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("block/water_still"));
        var pose=new PoseStack().last();var camera=e.getCamera().getPosition();
        for(var f:mesh){
            double x0=f.x0(),x1=f.x1(),y0=f.y0(),y1=f.y1(),z0=f.z0(),z1=f.z1();
            if(f.direction()!=net.minecraft.core.Direction.UP&&y0>=y)continue;
            double[][] corners=switch(f.direction()){
                case UP->new double[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}};
                case DOWN->new double[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}};
                case WEST->new double[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}};
                case EAST->new double[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}};
                case NORTH->new double[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}};
                case SOUTH->new double[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
            };
            var at=net.minecraft.core.BlockPos.containing(world((x0+x1)*.5,Math.min((y0+y1)*.5,y),(z0+z1)*.5));
            if(!mc.level.hasChunkAt(at))continue;
            int tint=net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(mc.level,at);
            int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,at);
            var d=f.direction();var n=world(d.getStepX(),d.getStepY(),d.getStepZ()).subtract(world(0,0,0));
            for(int i=0;i<4;i++)vertex(out,pose,camera,corners[i][0],corners[i][1],corners[i][2],sprite.getU(i<2?0:1),sprite.getV(i==0||i==3?0:1),tint,light,n);
            // Only the free water surface needs a reverse face when viewed from below. Keeping
            // floor/wall back faces culled avoids coplanar stripes against the enclosing wood.
            if(d==net.minecraft.core.Direction.UP)for(int i=3;i>=0;i--)vertex(out,pose,camera,corners[i][0],corners[i][1],corners[i][2],sprite.getU(i<2?0:1),sprite.getV(i==0||i==3?0:1),tint,light,n.scale(-1));
        }
        buffers.endBatch(GinsoWaterRenderType.WATER);
    }
    public static void register(RegisterGuiLayersEvent e){e.registerAboveAll(ResourceLocation.parse("orimod:ginso_escape"),(g,d)->{
        if(!active())return;var mc=Minecraft.getInstance();var camera=GinsoEscape.localPosition(state.origin(),state.turn(),mc.gameRenderer.getMainCamera().getPosition());
        if(camera.y<water()&&volume.contains(camera.x,camera.y,camera.z)&&clear.getOrDefault(GinsoFloodVolume.cell(camera.x,camera.y,camera.z),false))g.fill(0,0,g.guiWidth(),g.guiHeight(),0x3034A6C7);
        int remaining=(int)Math.ceil((state.limit()+GinsoEscapeRules.PREPARE_TICKS-age())/20);
        var text=age()<60?Component.translatable("hud.orimod.ginso.prepare",(int)Math.ceil((60-age())/20)):Component.translatable("hud.orimod.ginso.escape",remaining);
        g.drawCenteredString(mc.font,text,g.guiWidth()/2,38,0xB7F1F5);
        if(state.submerged()>0)g.drawCenteredString(mc.font,Component.translatable("hud.orimod.ginso.submerged",(60-state.submerged())/20f),g.guiWidth()/2,50,0xFFE5B3);
    });}
    public static Map<String,Object> diagnostics(){var m=new LinkedHashMap<String,Object>();m.put("active",active());m.put("phase",state==null?"READY":state.phase().name());m.put("floodBoxes",boxes.size());m.put("surfaceFaces",mesh.size());m.put("topFaces",mesh.stream().filter(f->f.direction()==net.minecraft.core.Direction.UP&&f.y1()>=water()-.5).count());m.put("volumeCells",cells.size());m.put("rimLocalY",cells.stream().mapToDouble(c->c.y()+.5).max().orElse(0));if(state!=null){m.put("waterY",state.origin().getY()+water());m.put("elapsedTicks",age());m.put("session",state.session().toString());}return m;}
    private GinsoEscapeClient(){}
}
