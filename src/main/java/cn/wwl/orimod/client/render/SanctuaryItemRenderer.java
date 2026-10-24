package cn.wwl.orimod.client.render;

import cn.wwl.orimod.block.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;

/** Buildings carry the same model as their placed core, fitted once into conventional item space. */
public final class SanctuaryItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static SanctuaryItemRenderer instance;
    private SanctuaryItemRenderer() {super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    public static void register(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if(instance==null)instance=new SanctuaryItemRenderer();return instance;
            }
        },OriBlocks.GLADES_WELL.get().asItem(),OriBlocks.FOREST_SHRINE.get().asItem(),OriBlocks.CREEK_SHRINE.get().asItem(),
                OriBlocks.CAVE_SHRINE.get().asItem(),OriBlocks.GLOOM_ALTAR.get().asItem(),OriBlocks.STORM_ALTAR.get().asItem(),
                OriBlocks.MAP_STONE.get().asItem(),OriBlocks.KEYSTONE_DOOR.get().asItem(),OriBlocks.GREAT_TREE_HEART.get().asItem(),
                OriBlocks.GINSO_WATER_HEART.get().asItem());
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(!(stack.getItem() instanceof BlockItem item))return;
        var state=item.getBlock().defaultBlockState();
        boolean door=state.is(OriBlocks.KEYSTONE_DOOR),map=state.is(OriBlocks.MAP_STONE),great=state.is(OriBlocks.GREAT_TREE_HEART);
        double height=great?3.65:door?3:map?2.25:2.8,width=map?1.4:3;
        float scale=(float)(.88/Math.max(height,width));
        pose.pushPose();pose.translate(.5,.5-height*scale/2,.5);pose.scale(scale,scale,scale);
        boolean ready;
        if(door) {
            var cells=new java.util.ArrayList<BlockPos>();
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)cells.add(new BlockPos(x,y,0));
            pose.translate(-1.5,0,-.5);
            ready=SanctuaryRenderer.available("keystone_gate");
            if(ready)SanctuaryRenderer.door(KeystoneDoorShape.of(cells,0),0,pose,buffers,light);
        } else {
            var mc=Minecraft.getInstance();double time=mc.level==null?0:mc.level.getGameTime();
            ready=SanctuaryRenderer.assembly(state,SanctuaryBlockEntity.Phase.DORMANT,true,time,pose,buffers,light);
        }
        pose.popPose();
        if(!ready) {
            // Resource-loading fallback is the block model, never an empty item or a recursively rendered BlockItem.
            var dispatcher=Minecraft.getInstance().getBlockRenderer();
            dispatcher.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutout(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)),
                    state,dispatcher.getBlockModel(state),1,1,1,light,overlay);
        }
    }
}
