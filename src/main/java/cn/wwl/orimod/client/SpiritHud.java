package cn.wwl.orimod.client;

import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.config.OriClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class SpiritHud {
    private SpiritHud(){}
    public static boolean enabled(){var p=Minecraft.getInstance().player;return p!=null && SpiritData.isSpirit(p) && !p.isSpectator()
            && (!OriClientConfig.SPEC.isLoaded() || !OriClientConfig.VANILLA_HUD.get());}
    public static void hideVanilla(RenderGuiLayerEvent.Pre e){
        if(!enabled())return;
        var n=e.getName();
        if(n.equals(VanillaGuiLayers.FOOD_LEVEL)||n.equals(VanillaGuiLayers.PLAYER_HEALTH)
                ||n.equals(VanillaGuiLayers.EXPERIENCE_BAR)||n.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))e.setCanceled(true);
    }
    public static void register(RegisterGuiLayersEvent e){
        e.registerBelow(VanillaGuiLayers.PLAYER_HEALTH,ResourceLocation.parse("orimod:life_energy"),(g,t)->meters(g));
        e.registerAbove(VanillaGuiLayers.AIR_LEVEL,ResourceLocation.parse("orimod:light_skills"),(g,t)->extras(g));
    }
    private static void meters(GuiGraphics g){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(!enabled()||mc.options.hideGui||mc.gameMode==null||!mc.gameMode.canHurtPlayer())return;
        int left=g.guiWidth()/2-91,right=g.guiWidth()/2+91;
        int lifeY=g.guiHeight()-mc.gui.leftHeight,energyY=g.guiHeight()-mc.gui.rightHeight;
        int life=p.hasEffect(MobEffects.WITHER)?0xA8A4BA:p.hasEffect(MobEffects.POISON)?0xB6DB40:p.getTicksFrozen()>0?0x82D9FF:0xFFFFFF;
        int lifeRows=row(g,"life",p.getHealth(),p.getMaxHealth(),2,left,lifeY,life,false);
        int energyRows=row(g,"energy",SpiritProgression.energy(p),SpiritProgression.maxEnergy(p),10,right,energyY,
                SpiritCombatClient.energyFlashing()?0xFF8870:0xFFFFFF,true);
        // Reserve exactly the rows drawn before vanilla armor/mount/air consume these counters.
        mc.gui.leftHeight+=lifeRows*10;mc.gui.rightHeight+=energyRows*10;
        String health=Math.round(p.getHealth())+"/"+Math.round(p.getMaxHealth());
        if(p.getAbsorptionAmount()>0)health+=" +"+Math.round(p.getAbsorptionAmount());
        smallText(g,health,left-4,lifeY,life,true);
        smallText(g,(int)SpiritProgression.energy(p)+"/"+(int)SpiritProgression.maxEnergy(p),right+4,energyY,0x82D9FF,false);
    }
    private static void extras(GuiGraphics g){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(!enabled()||mc.options.hideGui)return;
        int left=g.guiWidth()/2-91,right=g.guiWidth()/2+91;
        var light=Component.translatable("hud.orimod.light",p.getData(SpiritData.PROGRESS).light());
        g.drawString(mc.font,light,Math.max(4,left-8-mc.font.width(light)),g.guiHeight()-18,0xFFF0AE,true);
        boolean narrow=g.guiWidth()<400;
        SkillWheelScreen.hud(g,narrow?g.guiWidth()-82:right+12,
                narrow?g.guiHeight()-Math.max(mc.gui.leftHeight,mc.gui.rightHeight)-29:g.guiHeight()-27);
        if(mc.gameMode!=null && mc.gameMode.hasExperience()){
            int by=g.guiHeight()-29;
            g.fill(left,by,left+182,by+2,0xA01B2B25);g.fill(left,by,left+(int)(182*p.experienceProgress),by+2,0xFF83CF5C);
            if(p.experienceLevel>0)g.drawCenteredString(mc.font,""+p.experienceLevel,g.guiWidth()/2,by-6,0x83CF5C);
        }
    }
    private static void smallText(GuiGraphics g,String text,int x,int y,int color,boolean rightAligned){
        var font=Minecraft.getInstance().font;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.75f,.75f,1);
        g.drawString(font,text,rightAligned?-font.width(text):0,1,color,true);g.pose().popPose();
    }
    private static int row(GuiGraphics g,String kind,float value,float max,float base,int x,int y,int color,boolean right){
        float unit=SpiritHudRules.unit(max,base);int n=SpiritHudRules.cells(max,unit);
        g.setColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1);
        for(int i=0;i<n;i++)g.blitSprite(ResourceLocation.parse("orimod:hud/"+kind+"_"+SpiritHudRules.fill(value,unit,i)),
                SpiritHudRules.cellX(x,i,right),SpiritHudRules.cellY(y,i),9,9);
        g.setColor(1,1,1,1);
        if(right && SpiritCombatClient.energyFlashing())
            for(int r=0;r<SpiritHudRules.rows(n);r++)g.fill(x-82,y-r*10-1,x+1,y-r*10+10,0x55FF8870);
        return SpiritHudRules.rows(n);
    }
}
