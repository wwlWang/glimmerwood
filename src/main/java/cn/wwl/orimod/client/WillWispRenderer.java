package cn.wwl.orimod.client;

import cn.wwl.orimod.entity.WillWispEntity;
import cn.wwl.orimod.client.render.SpiritEnergy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Personal pickups: each participant sees their own light, not an overlapping stack of party rewards. */
public final class WillWispRenderer extends EntityRenderer<WillWispEntity>{
    public WillWispRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public void render(WillWispEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var p=Minecraft.getInstance().player;if(p==null || !p.getUUID().equals(e.owner()))return;
        float t=(e.tickCount+partial)*.06f;var out=buffers.getBuffer(SpiritEnergy.glow());var center=new Vector3f(0,(float)Math.sin(t)*.12f,0);
        SpiritEnergy.orb(out,pose.last(),center,.55f,e.kind().color,.55f);SpiritEnergy.orb(out,pose.last(),center,.14f,0xFFFFFF,.9f);
        for(int i=0;i<5;i++){double a=t+i*Math.PI*2/5;SpiritEnergy.orb(out,pose.last(),new Vector3f((float)Math.cos(a)*.3f,(float)Math.sin(a*.7)*.2f,(float)Math.sin(a)*.3f),.08f,e.kind().color,.55f);}
    }
    @Override public ResourceLocation getTextureLocation(WillWispEntity e){return SpiritEnergy.SOFT_GLOW;}
}
