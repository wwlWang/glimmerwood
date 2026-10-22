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
        if(n.equals(VanillaGuiLayers.FOOD_LEVEL)||n.equals(VanillaGuiLayers.PLAYER_HEALTH)||n.equals(VanillaGuiLayers.ARMOR_LEVEL)
                ||n.equals(VanillaGuiLayers.EXPERIENCE_BAR)||n.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))e.setCanceled(true);
    }
    public static void register(RegisterGuiLayersEvent e){e.registerAbove(VanillaGuiLayers.AIR_LEVEL,ResourceLocation.parse("orimod:life_energy"),(g,t)->render(g));}
    private static void render(GuiGraphics g){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(!enabled()||mc.options.hideGui||mc.screen!=null)return;
        int x=8,y=g.guiHeight()-90;
        int life=p.hasEffect(MobEffects.WITHER)?0xA8A4BA:p.hasEffect(MobEffects.POISON)?0xB6DB40:p.getTicksFrozen()>0?0x82D9FF:0xFFFFFF;
        row(g,"life",p.getHealth(),p.getMaxHealth(),2,x,y,life);
        g.drawString(mc.font,Component.translatable("hud.orimod.life",Math.round(p.getHealth()),Math.round(p.getMaxHealth())),x,y-11,life,false);
        if(p.getAbsorptionAmount()>0){int n=SpiritHudRules.cells(p.getMaxHealth(),SpiritHudRules.unit(p.getMaxHealth(),2));
            g.blitSprite(ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full"),x+n*10+3,y,9,9);
            g.drawString(mc.font,"+"+Math.round(p.getAbsorptionAmount()),x+n*10+14,y,0xFFDF69,false);
        }
        row(g,"energy",SpiritProgression.energy(p),SpiritProgression.maxEnergy(p),10,x,y+12,0xFFFFFF);
        int energyCells=SpiritHudRules.cells(SpiritProgression.maxEnergy(p),SpiritHudRules.unit(SpiritProgression.maxEnergy(p),10));
        g.drawString(mc.font,""+(int)SpiritProgression.energy(p),x+energyCells*10+3,y+12,0x82D9FF,false);
        int light=p.getData(SpiritData.PROGRESS).light();
        g.drawString(mc.font,Component.translatable("hud.orimod.light",light),x,y+24,0xFFF0AE,false);
        if(p.getArmorValue()>0)g.drawString(mc.font,Component.translatable("hud.orimod.armor",p.getArmorValue()),x,y+35,0xC5D6DF,false);
        SkillWheelScreen.hud(g,x,y+49);
        // Food is frozen in spirit form; keep vanilla air/mount rows. Thin XP retains both value and level.
        if(mc.gameMode!=null && mc.gameMode.hasExperience()){
            int bx=g.guiWidth()/2-91,by=g.guiHeight()-29;
            g.fill(bx,by,bx+182,by+2,0xA01B2B25);g.fill(bx,by,bx+(int)(182*p.experienceProgress),by+2,0xFF83CF5C);
            if(p.experienceLevel>0)g.drawString(mc.font,""+p.experienceLevel,bx+185,by-3,0x83CF5C,true);
        }
    }
    private static void row(GuiGraphics g,String kind,float value,float max,float base,int x,int y,int color){
        float unit=SpiritHudRules.unit(max,base);int n=SpiritHudRules.cells(max,unit);
        g.setColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1);
        for(int i=0;i<n;i++)g.blitSprite(ResourceLocation.parse("orimod:hud/"+kind+"_"+SpiritHudRules.fill(value,unit,i)),x+i*10,y,9,9);
        g.setColor(1,1,1,1);
    }
}
