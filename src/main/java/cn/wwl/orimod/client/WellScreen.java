package cn.wwl.orimod.client;
import cn.wwl.orimod.network.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.SpiritWell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
public final class WellScreen extends Screen {
    private final WellOfferPayload offer;
    private List<BlockPos> wells=List.of();
    private int page,left,top,w,h,rows;
    public WellScreen(WellOfferPayload p){super(Component.translatable("screen.orimod.well.choose"));offer=p;}
    public static void receive(WellOfferPayload p){var mc=Minecraft.getInstance();if(mc.player!=null && mc.level!=null && mc.level.dimension().location().toString().equals(p.dimension()))mc.setScreen(new WellScreen(p));}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        w=Math.min(330,width-20);h=Math.min(240,height-20);left=(width-w)/2;top=(height-h)/2;rows=Math.max(1,(h-80)/23);
        wells=SpiritWell.discovered(minecraft.player.getData(SpiritData.PROGRESS).rewards().keySet(),offer.dimension()).stream().filter(p->!p.equals(offer.from())).toList();
        page=Math.clamp(page,0,Math.max(0,(wells.size()-1)/rows));
        for(int i=0;i<rows && page*rows+i<wells.size();i++){
            BlockPos p=wells.get(page*rows+i);int distance=(int)Math.sqrt(p.distSqr(offer.from()));
            var label=Component.translatable("screen.orimod.well.destination",p.getX(),p.getY(),p.getZ(),distance);
            addRenderableWidget(new SpiritButton(left+12,top+38+i*23,w-24,20,label,b->{
                PacketDistributor.sendToServer(new WellSelectPayload(offer.from(),p,offer.dimension()));onClose();
            }));
        }
        var prev=addRenderableWidget(new SpiritButton(left+12,top+h-28,42,20,Component.literal("<"),b->{page--;rebuildWidgets();}));prev.active=page>0;
        var next=addRenderableWidget(new SpiritButton(left+w-54,top+h-28,42,20,Component.literal(">"),b->{page++;rebuildWidgets();}));next.active=(page+1)*rows<wells.size();
        addRenderableWidget(new SpiritButton(left+w/2-45,top+h-28,90,20,Component.translatable("gui.back"),b->onClose()));
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xA00A161C);g.fill(left,top,left+w,top+h,0xF0142C30);g.renderOutline(left,top,w,h,0xFF5C8A82);
        g.drawString(font,title,left+12,top+12,0xB6F6DF,false);
        if(wells.isEmpty())g.drawWordWrap(font,Component.translatable("message.orimod.well.none"),left+12,top+40,w-24,0xC6DCD7);
        super.render(g,mx,my,partial);
    }
}
