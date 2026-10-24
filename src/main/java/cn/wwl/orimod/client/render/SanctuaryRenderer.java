package cn.wwl.orimod.client.render;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.SpiritWellBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.gltf.GltfMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/** Static glTF triangles through vanilla buffers: world light on stone, additive emission on water/engravings. */
public final class SanctuaryRenderer implements BlockEntityRenderer<SanctuaryBlockEntity> {
    public SanctuaryRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public AABB getRenderBoundingBox(SanctuaryBlockEntity b) {
        if (b.getBlockState().is(OriBlocks.GREAT_TREE_HEART))
            return new AABB(b.getBlockPos()).inflate(1,0,1).expandTowards(0,2.7,0);
        if (b.getBlockState().is(OriBlocks.KEYSTONE_DOOR) && b.getLevel()!=null) {
            var cells=KeystoneDoorBlock.door(b.getLevel(),b.getBlockPos());
            if (!cells.isEmpty()) return KeystoneDoorShape.of(cells,0).bounds().inflate(.1);
        }
        return new AABB(b.getBlockPos()).inflate(1.05,0,1.05).expandTowards(0,2.2+floorLift(b),0);
    }
    @Override public void render(SanctuaryBlockEntity b,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if (b.getLevel()==null) return;
        var state=b.getBlockState();double time=b.getLevel().getGameTime()+partial;
        if (state.is(OriBlocks.KEYSTONE_DOOR)) {
            if (!available("keystone_gate")) {fallback(b,pose,buffers,light,overlay);return;}
            var cells=KeystoneDoorBlock.door(b.getLevel(),b.getBlockPos());if(cells.isEmpty())return;
            var shape=KeystoneDoorShape.of(cells,state.getValue(KeystoneDoorBlock.FILLED));
            if (!shape.root().equals(b.getBlockPos())) return;
            pose.pushPose();pose.translate(-b.getBlockPos().getX(),-b.getBlockPos().getY(),-b.getBlockPos().getZ());
            door(shape,0,pose,buffers,light);pose.popPose();return;
        }
        pose.pushPose();pose.translate(.5,0,.5);
        var companion=legacyNeighbour(b);
        var fit=SanctuaryPresentation.fit(companion,state.is(OriBlocks.CAVE_SHRINE));
        pose.translate(fit.x(),0,fit.z());pose.scale(fit.horizontal(),fit.vertical(),fit.horizontal());
        if(state.is(OriBlocks.GLOOM_ALTAR)||state.is(OriBlocks.STORM_ALTAR))pose.translate(0,floorLift(b),0);
        boolean ready=assembly(state,b.phase(),b.activated(),time,pose,buffers,light);
        pose.popPose();
        if (!ready) fallback(b,pose,buffers,light,overlay);
    }

    /** Shared world/item assembly; the caller owns placement, scale, legacy fitting and render context. */
    static boolean assembly(net.minecraft.world.level.block.state.BlockState state,SanctuaryBlockEntity.Phase phase,
                            boolean activated,double time,PoseStack pose,MultiBufferSource buffers,int light) {
        boolean ready;
        if (state.is(OriBlocks.GINSO_WATER_HEART)) {
            ready=mesh("ginso_water_roots",pose,buffers,light,.7f,1);
            pose.translate(0,1.12+Math.sin(time*.04)*.035,0);pose.mulPose(Axis.YP.rotation((float)time*.006f));
            mesh("ginso_water_crystal",pose,buffers,light,.75f,1);
        } else if (state.is(OriBlocks.GREAT_TREE_HEART)) {
            boolean bloomed=state.getValue(SpiritTreeHeartBlock.BLOOMED);
            ready=mesh("great_heart_roots",pose,buffers,light,.45f,1);
            pose.translate(0,1.75+Math.sin(time*.025)*.035,0);
            pose.mulPose(Axis.YP.rotation((float)time*.003f));
            mesh("great_heart_crystal",pose,buffers,light,bloomed?1:.65f,1);
        } else if (state.getBlock() instanceof SpiritWellBlock) {
            pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(SpiritWellBlock.FACING).toYRot()));
            String theme=state.is(OriBlocks.FOREST_SHRINE)?"forest":state.is(OriBlocks.CREEK_SHRINE)?"creek":state.is(OriBlocks.CAVE_SHRINE)?"cave":"well";
            ready=mesh("basin_"+theme,pose,buffers,light,1,1);
        } else if (state.is(OriBlocks.MAP_STONE)) {
            ready=mesh("map_pillar",pose,buffers,light,activated?1:.08f,1);
            pose.translate(0,2.02+Math.sin(time*.035)*.035,0);pose.mulPose(Axis.YP.rotation((float)time*.006f));
            pose.mulPose(Axis.XP.rotationDegrees(12));mesh("map_disc",pose,buffers,light,activated?1:.12f,1);
        } else {
            boolean storm=state.is(OriBlocks.STORM_ALTAR);boolean active=phase==SanctuaryBlockEntity.Phase.ACTIVE;
            boolean won=phase==SanctuaryBlockEntity.Phase.DEFEATED;
            float glow=active?1.1f:won?.65f:.18f;
            ready=mesh(storm?"altar_storm":"altar_gloom",pose,buffers,light,glow,1);
            pose.translate(0,1.92+Math.sin(time*.045)*.06,0);
            pose.mulPose(Axis.YP.rotation((float)time*(active?.035f:.008f)));
            if (won) pose.scale(.65f,.65f,.65f);
            if(storm)pose.mulPose(Axis.XP.rotationDegrees(62));
            mesh(storm?"storm_ring":"gloom_crystal",pose,buffers,light,glow,won?.65f:1);
            if (won) {pose.scale(.5f,.5f,.5f);mesh("keystone_inset",pose,buffers,LightTexture.FULL_BRIGHT,1,1);}
        }
        return ready;
    }
    private static net.minecraft.core.Direction legacyNeighbour(SanctuaryBlockEntity b) {
        var state=b.getBlockState();
        if(b.getLevel()==null || !(state.is(OriBlocks.CAVE_SHRINE)||state.is(OriBlocks.MAP_STONE)))return null;
        for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var p=b.getBlockPos().relative(dir);
            if(b.getLevel().hasChunkAt(p) && b.getLevel().getBlockState(p).is(state.is(OriBlocks.CAVE_SHRINE)?OriBlocks.MAP_STONE:OriBlocks.CAVE_SHRINE))return dir;
        }
        return null;
    }
    public static void door(KeystoneDoorShape s,float opening,PoseStack pose,MultiBufferSource buffers,int light) {
        var centre=s.bounds().getCenter();
        pose.pushPose();pose.translate(centre.x,s.bounds().minY,centre.z);
        if(s.alongZ())pose.mulPose(Axis.YP.rotationDegrees(90));
        float scale=1-opening*opening;
        pose.translate(0,opening*s.height()*.65,0);
        pose.scale((float)s.width()*scale,(float)s.height()*scale,Math.max(.12f,scale));
        mesh("keystone_gate",pose,buffers,light,.2f+.8f*opening,1);
        // Door groups predate a facing property. Both approaches must show the shared slots.
        for(double face:new double[]{.105,-.22}) for(int i=0;i<2;i++) {
            pose.pushPose();pose.translate(i==0?-.15:.15,.5,face);
            mesh("keystone_inset",pose,buffers,light,i<s.filled()?1:0,i<s.filled()?1:.22f);
            pose.popPose();
        }
        pose.popPose();
    }
    /** Old cave altars replace a floor block; a placed-on-top altar keeps its usual origin.
     * This is only a visual offset, with the render bounds following it; no terrain or collision writes. */
    private static int floorLift(SanctuaryBlockEntity b) {
        if (b.getLevel()==null || !(b.getBlockState().is(OriBlocks.GLOOM_ALTAR) || b.getBlockState().is(OriBlocks.STORM_ALTAR))) return 0;
        int supports=0;
        for (var dir:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var pos=b.getBlockPos().relative(dir);
            if (b.getLevel().hasChunkAt(pos) && b.getLevel().getBlockState(pos).isFaceSturdy(b.getLevel(),pos,net.minecraft.core.Direction.UP)) supports++;
        }
        return supports>=3?1:0;
    }
    static boolean mesh(String name,PoseStack pose,MultiBufferSource buffers,int light,float emission,float brightness) {
        var model=GemRenderModels.handle(ResourceLocation.fromNamespaceAndPath("orimod","models/sanctuary/"+name+".glb")).get();
        if(model==null)return false;
        var textures=Minecraft.getInstance().getTextureManager();
        for(var part:model.model().meshes())if(textures.getTexture(part.material().texture(),null)==null)return false;
        var point=new Vector3f();var normal=new Vector3f();
        for(int pass=0;pass<2;pass++) {
            if(pass==1 && emission<=0)continue;
            int colour=Math.clamp(Math.round(255*(pass==0?brightness:emission)),0,255);
            for(var part:model.model().meshes()) {
                if(!(part.mesh() instanceof GltfMesh mesh))continue;
                var out=buffers.getBuffer(pass==0?SanctuaryRenderType.stone(part.material().texture()):SanctuaryRenderType.emission(part.material().texture()));
                var g=mesh.geometry();
                for(int j=0;j<g.indexCount();j++) {
                    int i=g.index(j);pose.last().pose().transformPosition(point.set(g.position(i,0),g.position(i,1),g.position(i,2)));
                    pose.last().normal().transform(normal.set(g.normal(i,0),g.normal(i,1),g.normal(i,2))).normalize();
                    out.addVertex(point.x,point.y,point.z).setColor(colour,colour,colour,255)
                            .setUv(g.texCoord(i,0),g.texCoord(i,1)+(pass==0?0:SpiritSelfLight.EMISSION_UV_OFFSET))
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(pass==0?light:LightTexture.FULL_BRIGHT).setNormal(normal.x,normal.y,normal.z);
                }
            }
        }
        return true;
    }
    static boolean available(String name) {
        var model=GemRenderModels.handle(ResourceLocation.fromNamespaceAndPath("orimod","models/sanctuary/"+name+".glb")).get();
        return model!=null && model.model().meshes().stream().allMatch(p->Minecraft.getInstance().getTextureManager().getTexture(p.material().texture(),null)!=null);
    }
    private static void fallback(SanctuaryBlockEntity b,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var dispatcher=Minecraft.getInstance().getBlockRenderer();
        dispatcher.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutout(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)),
                b.getBlockState(),dispatcher.getBlockModel(b.getBlockState()),1,1,1,light,overlay);
    }
}
