package cn.wwl.orimod.client;

import cn.wwl.orimod.entity.GloomBeetle;
import cn.wwl.orimod.entity.BeetleRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original block-built silhouette: broad split carapace, low belly, six articulated legs. */
public final class GloomBeetleRenderer extends EntityRenderer<GloomBeetle> {
    private static final ResourceLocation SKIN=ResourceLocation.parse("orimod:textures/entity/gloom_beetle.png");
    private final ModelPart body,shell,eyes;
    private final ModelPart[] legs=new ModelPart[6];
    public GloomBeetleRenderer(EntityRendererProvider.Context context){
        super(context);shadowRadius=.6f;var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,24).addBox(-6,-3,-5,12,4,10),PartPose.ZERO);
        root.addOrReplaceChild("shell",CubeListBuilder.create().texOffs(0,0).addBox(-7,0,-6,14,4,12),PartPose.ZERO);
        root.addOrReplaceChild("eyes",CubeListBuilder.create().texOffs(48,0).addBox(-4,-1,5,2,2,2).addBox(2,-1,5,2,2,2),PartPose.ZERO);
        for(int side=0;side<2;side++)for(int row=0;row<3;row++){
            int i=side*3+row;root.addOrReplaceChild("leg"+i,CubeListBuilder.create().texOffs(0,42).addBox(side==0?-4:0,-1,-1,4,2,2),PartPose.offset(side==0?-5:5,-2,row*4-4));
        }
        var baked=LayerDefinition.create(mesh,64,64).bakeRoot();body=baked.getChild("body");shell=baked.getChild("shell");eyes=baked.getChild("eyes");
        for(int i=0;i<6;i++)legs[i]=baked.getChild("leg"+i);
    }
    @Override public void render(GloomBeetle beetle,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if (cn.wwl.orimod.client.render.GloomCreatureVisual.hideVanilla(beetle)) { super.render(beetle, yaw, partial, pose, buffers, light); return; }
        var state=beetle.state();float t=beetle.tickCount+partial;
        boolean windup=state==BeetleRules.State.WINDUP,flipped=state==BeetleRules.State.FLIPPED;
        pose.pushPose();pose.translate(0,.35,0);pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,beetle.yBodyRotO,beetle.yBodyRot)));
        pose.mulPose(Axis.ZP.rotationDegrees(beetle.roll(partial)));
        var consumer=buffers.getBuffer(RenderType.entityCutout(SKIN));
        body.render(pose,consumer,light,OverlayTexture.NO_OVERLAY);
        shell.render(pose,consumer,windup?LightTexture.FULL_BRIGHT:light,OverlayTexture.NO_OVERLAY);
        eyes.render(pose,consumer,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        float stride=flipped?.16f:windup?.3f:(float)Math.min(.4,beetle.getDeltaMovement().horizontalDistance()*2);
        for(int i=0;i<6;i++){
            legs[i].yRot=Mth.sin(t*(windup?.9f:.6f)+i*2)*stride;
            legs[i].zRot=(i<3?1:-1)*(.35f+(flipped?Mth.sin(t*.8f+i)*.2f:0));
            legs[i].render(pose,consumer,light,OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();super.render(beetle,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(GloomBeetle entity){return SKIN;}
}
