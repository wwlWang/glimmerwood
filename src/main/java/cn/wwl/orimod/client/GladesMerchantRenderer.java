package cn.wwl.orimod.client;
import cn.wwl.orimod.entity.GladesMerchant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original round-eared, long-tailed forest residents, assembled from hand-authored cuboids. */
public final class GladesMerchantRenderer extends EntityRenderer<GladesMerchant> {
    private final ModelPart body,head,tail;
    public GladesMerchantRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=.3f;var mesh=new MeshDefinition();var r=mesh.getRoot();
        r.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,22).addBox(-4,3,-3,8,9,6).texOffs(0,44).addBox(-4,0,-2,3,3,5).addBox(1,0,-2,3,3,5)
                .texOffs(32,22).addBox(-6,5,-1,2,7,3).addBox(4,5,-1,2,7,3),PartPose.ZERO);
        var h=r.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,0,-3.5f,9,7,7)
                .texOffs(34,0).addBox(-7,4,-1,4,4,2).addBox(3,4,-1,4,4,2)
                .texOffs(48,12).addBox(-3,3,3.5f,2,2,1).addBox(1,3,3.5f,2,2,1)
                .texOffs(40,14).addBox(-2,1,3,4,2,2),PartPose.offset(0,11,0));
        r.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(30,38).addBox(-1.5f,-1,-7,3,3,8).texOffs(30,52).addBox(-1.5f,1,-8,3,3,3),PartPose.offset(0,4,-2));
        var baked=LayerDefinition.create(mesh,64,64).bakeRoot();body=baked.getChild("body");head=baked.getChild("head");tail=baked.getChild("tail");
    }
    @Override public void render(GladesMerchant e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();float bodyYaw=Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot);pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        var v=buffers.getBuffer(RenderType.entityCutout(getTextureLocation(e)));body.render(pose,v,light,OverlayTexture.NO_OVERLAY);
        head.yRot=-(Mth.rotLerp(partial,e.yHeadRotO,e.yHeadRot)-bodyYaw)*Mth.DEG_TO_RAD;head.xRot=-Mth.lerp(partial,e.xRotO,e.getXRot())*Mth.DEG_TO_RAD;
        head.render(pose,v,light,OverlayTexture.NO_OVERLAY);tail.yRot=Mth.sin((e.tickCount+partial)*.055f)*.18f;tail.render(pose,v,light,OverlayTexture.NO_OVERLAY);pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
    }
    @Override protected boolean shouldShowName(GladesMerchant e){return entityRenderDispatcher.distanceToSqr(e)<64;}
    @Override public ResourceLocation getTextureLocation(GladesMerchant e){return ResourceLocation.parse("orimod:textures/entity/glades_merchant_"+e.job()+".png");}
}
