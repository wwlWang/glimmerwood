package cn.wwl.orimod.client;
import cn.wwl.orimod.network.*;
import cn.wwl.orimod.progression.Shard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class MerchantScreen extends Screen {
    private MerchantOfferPayload offer;private int left,top,panelWidth,panelHeight,buyWidth;
    private int rowHeight(){return offer.job()==1?30:42;}
    public MerchantScreen(MerchantOfferPayload offer){super(Component.translatable(offer.job()==0?"merchant.orimod.mosswhisper":"merchant.orimod.lulu"));this.offer=offer;}
    public static void receive(MerchantOfferPayload packet){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof MerchantScreen screen && screen.offer.merchant().equals(packet.merchant())){
            if(packet.message().equals("closed")){mc.setScreen(null);return;}screen.offer=packet;screen.rebuildWidgets();
        }else if(!packet.message().equals("closed") && packet.message().isEmpty())mc.setScreen(new MerchantScreen(packet));
    }
    @Override protected void init(){
        panelWidth=Math.min(360,width-20);panelHeight=Math.min(252,height-12);left=(width-panelWidth)/2;top=(height-panelHeight)/2;clearWidgets();
        buyWidth=offer.offers().stream().mapToInt(row->font.width(Component.translatable("merchant.orimod.buy",row.price()))+16).max().orElse(80);
        for(int i=0;i<offer.offers().size();i++){
            var row=offer.offers().get(i);var button=addRenderableWidget(new SpiritButton(left+panelWidth-12-buyWidth,top+48+i*rowHeight(),buyWidth,20,Component.translatable("merchant.orimod.buy",row.price()),b->purchase(row.id())));
            button.active=!offer.busy() && row.available() && offer.balance()>=row.price();
        }
        addRenderableWidget(new SpiritButton(left+panelWidth-80,top+panelHeight-28,70,20,Component.translatable("gui.done"),b->onClose()));
    }
    private void purchase(String id){PacketDistributor.sendToServer(new MerchantBuyPayload(offer.entityId(),offer.merchant(),offer.day(),offer.token(),id));offer=new MerchantOfferPayload(offer.entityId(),offer.merchant(),offer.day(),offer.token(),offer.job(),offer.balance(),offer.offers(),"searching",true);rebuildWidgets();}
    @Override public void onClose(){PacketDistributor.sendToServer(new MerchantBuyPayload(offer.entityId(),offer.merchant(),offer.day(),offer.token(),"close"));super.onClose();}
    @Override public void removed(){if(minecraft!=null && minecraft.getConnection()!=null)PacketDistributor.sendToServer(new MerchantBuyPayload(offer.entityId(),offer.merchant(),offer.day(),offer.token(),"close"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.fill(0,0,width,height,0xB309181E);g.fill(left,top,left+panelWidth,top+panelHeight,0xF0172B32);g.fill(left,top,left+panelWidth,top+2,0xFF8CDAC8);
        g.drawString(font,title,left+12,top+10,0xB6F6DF,false);g.drawString(font,Component.translatable("screen.orimod.light",offer.balance()),left+12,top+25,0xF2D892,false);
        for(int i=0;i<offer.offers().size();i++){
            var row=offer.offers().get(i);var shard=Shard.byId(row.id());var name=shard==null?Component.translatable("merchant.orimod.guide."+row.id()):shard.title();
            g.drawWordWrap(font,name,left+12,top+48+i*rowHeight(),panelWidth-36-buyWidth,row.available()?0xD7EBEC:0x7E999E);
            if(shard!=null){
                String description=shard.description().getString();int available=panelWidth-24;
                String brief=font.width(description)<=available?description:font.plainSubstrByWidth(description,available-font.width("…"))+"…";
                g.drawString(font,brief,left+12,top+72+i*rowHeight(),0x9CBEC2,false);
            }
            if(!row.available())g.drawString(font,Component.translatable("progress.orimod.already_owned"),left+12,top+62+i*rowHeight(),0x7E999E,false);
        }
        String message=offer.message().isEmpty()?(offer.job()==1?"cartography":offer.offers().isEmpty()?"empty":"daily"):offer.message();
        g.drawWordWrap(font,Component.translatable("merchant.orimod."+message),left+12,top+176,panelWidth-24,0xD8C69B);super.render(g,x,y,partial);
        for(int i=0;i<offer.offers().size();i++)if(x>=left+12 && x<left+panelWidth-12 && y>=top+72+i*rowHeight() && y<top+83+i*rowHeight()){
            var shard=Shard.byId(offer.offers().get(i).id());if(shard!=null)g.renderTooltip(font,font.split(shard.description(),Math.min(300,width-30)),x,y);
        }
    }
    public Map<String,Object> debug(){return Map.of("open",true,"entityId",offer.entityId(),"job",offer.job(),"day",offer.day(),"balance",offer.balance(),"busy",offer.busy(),"message",offer.message(),"offers",offer.offers().stream().map(o->Map.of("id",o.id(),"price",o.price(),"available",o.available())).toList());}
}
