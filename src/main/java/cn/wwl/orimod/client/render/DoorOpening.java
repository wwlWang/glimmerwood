package cn.wwl.orimod.client.render;

import cn.wwl.orimod.block.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** A short-lived visual snapshot. The authoritative doorway is already air, so opening never delays escape. */
public final class DoorOpening {
    private record Opening(KeystoneDoorShape shape, Set<BlockPos> cells, long start) {}
    public static final int TICKS=24, LIMIT=32;
    private static final List<Opening> OPENINGS=new ArrayList<>();
    private static Level world;
    public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {OPENINGS.clear();world=null;}
    private static void world(Level level) {if(world!=level){OPENINGS.clear();world=level;}}
    public static void removing(Level level,BlockPos pos,BlockState next) {
        if(!level.isClientSide || !next.isAir())return;
        var old=level.getBlockState(pos);
        if(!old.is(OriBlocks.KEYSTONE_DOOR) || old.getValue(KeystoneDoorBlock.FILLED)!=1)return;
        world(level);OPENINGS.removeIf(o->level.getGameTime()-o.start>=TICKS);
        if(OPENINGS.stream().anyMatch(o->o.cells.contains(pos)))return;
        var cells=KeystoneDoorBlock.door(level,pos);if(cells.isEmpty())return;
        if(OPENINGS.size()==LIMIT)OPENINGS.removeFirst();
        OPENINGS.add(new Opening(KeystoneDoorShape.of(cells,2),Set.copyOf(cells),level.getGameTime()));
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();world(mc.level);if(mc.level==null)return;
        double now=mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        OPENINGS.removeIf(o->now-o.start>=TICKS);
        if(OPENINGS.isEmpty())return;
        var buffers=mc.renderBuffers().bufferSource();var camera=event.getCamera().getPosition();
        for(var o:OPENINGS) {
            // A creative player may remove just one filled cell. Animate only the complete opening,
            // and stop if a new door is placed into this footprint while the old visual dissolves.
            if(o.cells.stream().anyMatch(p->!mc.level.hasChunkAt(p) || mc.level.getBlockState(p).is(OriBlocks.KEYSTONE_DOOR)))continue;
            if(o.shape.bounds().getCenter().distanceToSqr(camera)>64*64)continue;
            var pose=new PoseStack();pose.translate(-camera.x,-camera.y,-camera.z);
            SanctuaryRenderer.door(o.shape,(float)Math.max(0,(now-o.start)/TICKS),pose,buffers,LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch();
    }
    private DoorOpening() {}
}
