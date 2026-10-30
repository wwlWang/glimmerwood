package cn.wwl.orimod.client.render;

import cn.wwl.orimod.entity.CorruptedAnimals;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Original animated mesh, species-specific UV eye masks; no replacement animal models. */
public final class CorruptAnimalLayer<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
    private final RenderType eyes;
    public CorruptAnimalLayer(RenderLayerParent<T,M> parent,ResourceLocation texture){super(parent);eyes=RenderType.eyes(texture);}
    public static int tint(LivingEntity e,int color){
        if(!CorruptedAnimals.infected(e))return color;
        int r=(color>>16&255)*105/255,g=(color>>8&255)*48/255,b=(color&255)*146/255;
        return color&0xff000000|r<<16|g<<8|b;
    }
    @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,T entity,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(!CorruptedAnimals.infected(entity)||entity.isInvisible())return;
        getParentModel().renderToBuffer(poses,buffers.getBuffer(eyes),0xf000f0,OverlayTexture.NO_OVERLAY,0xffffb4ff);
    }
    /** Each rectangle is in the vanilla texture's pixel grid. Transparent pixels never draw on the body. */
    private static ResourceLocation mask(String species,int width,int height,int[][] rectangles){
        var image=new NativeImage(width,height,true);
        for(var r:rectangles)for(int y=r[1];y<r[1]+r[3];y++)for(int x=r[0];x<r[0]+r[2];x++)image.setPixelRGBA(x,y,0xffffffff);
        var id=ResourceLocation.fromNamespaceAndPath("orimod","corruption_eyes/"+species);
        Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(image));return id;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void add(EntityRenderersEvent.AddLayers e,EntityType type,ResourceLocation texture){
        if(e.getRenderer(type) instanceof LivingEntityRenderer renderer)renderer.addLayer(new CorruptAnimalLayer(renderer,texture));
    }
    public static void layers(EntityRenderersEvent.AddLayers e){
        add(e,EntityType.COW,mask("cow",64,32,new int[][]{{6,8,2,2},{12,8,2,2}}));
        add(e,EntityType.PIG,mask("pig",64,32,new int[][]{{8,10,2,2},{14,10,2,2}}));
        add(e,EntityType.SHEEP,mask("sheep",64,32,new int[][]{{8,10,2,1},{12,10,2,1}}));
        add(e,EntityType.CHICKEN,mask("chicken",64,32,new int[][]{{3,4,1,1},{6,4,1,1}}));
        add(e,EntityType.RABBIT,mask("rabbit",64,32,new int[][]{{38,5,1,2},{40,5,1,2}}));
        var horse=mask("horse",64,64,new int[][]{{3,21,2,1},{16,21,2,1}});
        add(e,EntityType.HORSE,horse);add(e,EntityType.DONKEY,horse);add(e,EntityType.MULE,horse);
    }
}
