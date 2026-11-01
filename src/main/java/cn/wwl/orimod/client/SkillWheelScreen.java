package cn.wwl.orimod.client;

import cn.wwl.orimod.progression.WheelSkill;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.network.SkillBindPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Non-pausing screen: releasing G closes it, mouse look is naturally ungrabbed. */
public final class SkillWheelScreen extends Screen {
    private List<WheelSkill> skills=List.of();
    private int hovered;
    private int cx,cy;
    private double outer;
    public SkillWheelScreen(){super(Component.translatable("screen.orimod.skill_wheel"));}
    @Override protected void init(){
        skills=Arrays.stream(WheelSkill.values()).filter(s->minecraft.player!=null&&s.learned(minecraft.player)).toList();
        cx=width/2;cy=height/2;outer=Math.min(103,height*.37);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){SkillControls.closeWheel();}
    @Override public void mouseMoved(double x,double y){
        double dx=x-cx,dy=y-cy;
        if(dx*dx+dy*dy<35*35||skills.isEmpty())return;
        double a=Math.atan2(dy,dx)+Math.PI/2+Math.PI/skills.size();
        hovered=Math.floorMod((int)Math.floor(a/(Math.PI*2/skills.size())),skills.size());
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(!skills.isEmpty()&&vertical!=0)hovered=Math.floorMod(hovered+(vertical>0?-1:1),skills.size());return true;
    }
    @Override public boolean keyPressed(int code,int scan,int modifiers){
        for(int i=0;i<3;i++)if(SkillControls.key(i).isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(code,scan))){
            SkillControls.bindPressed(i);
            if(!skills.isEmpty())PacketDistributor.sendToServer(new SkillBindPayload(i,skills.get(hovered)));
            return true;
        }
        if(SpiritControls.PANEL.matches(code,scan))return true;
        return super.keyPressed(code,scan,modifiers);
    }
    @Override public boolean keyReleased(int code,int scan,int modifiers){
        if(SpiritControls.PANEL.matches(code,scan)){minecraft.setScreen(null);return true;}
        for(int i=0;i<3;i++)if(SkillControls.key(i).matches(code,scan)){SkillControls.slotReleased(i);return true;}
        return super.keyReleased(code,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xA5101A24);
        g.drawCenteredString(font,title,cx,Math.max(5,(int)(cy-outer)-22),0xD5F7FF);
        var bindings=minecraft.player.getData(SpiritData.SKILL_BINDINGS);
        for(int i=0;i<skills.size();i++){
            double angle=-Math.PI/2+i*Math.PI*2/skills.size();
            sector(g,angle-Math.PI/skills.size()+.025,angle+Math.PI/skills.size()-.025,37,outer,
                    i==hovered?0xDF326D87:0xCF152F42);
            var skill=skills.get(i);int x=cx+(int)(Math.cos(angle)*outer*.70),y=cy+(int)(Math.sin(angle)*outer*.70);
            icon(g,skill,x-10,y-15,20);
            g.drawCenteredString(font,Component.translatable(skill.titleKey()),x,y+7,i==hovered?0xFFFFFF:0xA9C9D5);
            int slot=bindings.slots().indexOf(skill.id());
            if(slot>=0){String key=SkillControls.key(slot).getTranslatedKeyMessage().getString();
                g.fill(x+7,y-20,x+12+font.width(key),y-9,0xFF214052);g.drawString(font,key,x+10,y-19,0xFFE29B,false);}
        }
        for(int i=0;i<3;i++){
            int x=cx-28+i*20;icon(g,bindings.skill(i),x,cy-9,16);
            g.drawCenteredString(font,SkillControls.key(i).getTranslatedKeyMessage(),x+8,cy+9,0xFFE29B);
        }
        g.drawCenteredString(font,Component.translatable("screen.orimod.skill_wheel.bind"),cx,Math.min(height-23,(int)(cy+outer)+5),0xCBDCE4);
        g.drawCenteredString(font,Component.translatable("screen.orimod.skill_wheel.close",SpiritControls.PANEL.getTranslatedKeyMessage()),cx,Math.min(height-11,(int)(cy+outer)+17),0x8DA6B5);
    }
    private void sector(GuiGraphics g,double from,double to,double inner,double radius,int color){
        var v=g.bufferSource().getBuffer(RenderType.gui());var m=g.pose().last().pose();
        for(int j=0;j<32;j++){
            double a=from+(to-from)*j/32,b=from+(to-from)*(j+1)/32;
            // Match GuiGraphics.fill's winding (GUI Y points down); RenderType.gui culls the reverse face.
            v.addVertex(m,(float)(cx+Math.cos(b)*inner),(float)(cy+Math.sin(b)*inner),0).setColor(color);
            v.addVertex(m,(float)(cx+Math.cos(b)*radius),(float)(cy+Math.sin(b)*radius),0).setColor(color);
            v.addVertex(m,(float)(cx+Math.cos(a)*radius),(float)(cy+Math.sin(a)*radius),0).setColor(color);
            v.addVertex(m,(float)(cx+Math.cos(a)*inner),(float)(cy+Math.sin(a)*inner),0).setColor(color);
        }
        g.flush();
    }
    public static void icon(GuiGraphics g,WheelSkill skill,int x,int y,int size){
        g.blit(ResourceLocation.parse("orimod:textures/gui/skills/"+(skill==null?"empty_slot":skill.id())+".png"),x,y,size,size,0,0,32,32,32,32);
    }
    public static void hud(GuiGraphics g,int x,int y){
        var mc=Minecraft.getInstance();var p=mc.player;var slots=p.getData(SpiritData.SKILL_BINDINGS);var ready=p.getData(SpiritData.SKILL_READY);
        for(int i=0;i<3;i++){
            var skill=slots.skill(i);int xx=x+i*29;icon(g,skill,xx,y,16);
            long ticks=i<ready.size()?Math.max(0,ready.get(i)-p.level().getGameTime()):0;
            boolean lacks=skill!=null&&cn.wwl.orimod.progression.SpiritProgression.energyForUse(p)<skill.cost;
            if(ticks>0){g.fill(xx,y,xx+16,y+16,0x99101929);g.drawCenteredString(mc.font,""+(ticks+19)/20,xx+8,y+4,0xFFFFFF);}
            else if(lacks)g.fill(xx,y,xx+16,y+16,0x77552230);
            g.drawCenteredString(mc.font,SkillControls.key(i).getTranslatedKeyMessage(),xx+8,y+17,lacks?0xEC879B:0xCADDE8);
        }
    }
    public Map<String,Object> diagnostics(){return Map.of("open",true,"hovered",skills.isEmpty()?"":skills.get(hovered).id(),
            "skills",skills.stream().map(WheelSkill::id).toList(),"centre",new int[]{cx,cy},"radius",outer);}
}
