package cn.wwl.orimod.devtools;

import cn.wwl.orimod.block.SanctuaryAssembly;
import cn.wwl.orimod.block.SanctuaryShape;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Development-only actual collision overlay. No registration or measurement in release builds. */
final class SanctuaryCollisionPreview {
    private static Object level;
    private static BlockPos core;
    static Map<String,Object> select(BlockPos p,boolean enabled){
        var mc=Minecraft.getInstance();
        core=enabled?p.immutable():null;level=enabled?mc.level:null;
        if(core==null)return Map.of("enabled",false);
        var owner=SanctuaryAssembly.owner(core,mc.level.getBlockState(core));
        if(owner==null)throw new IllegalArgumentException("An assembled core is required");
        core=owner.core();
        var cells=new ArrayList<Map<String,Object>>();
        for(var c:SanctuaryShape.cells(owner.kind(),owner.facing())){
            var pos=core.offset(c.offset());var s=mc.level.getBlockState(pos);
            cells.add(Map.of("position",List.of(pos.getX(),pos.getY(),pos.getZ()),"state",s.toString(),
                    "boxes",s.getCollisionShape(mc.level,pos).toAabbs().stream().map(b->List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList()));
        }
        return Map.of("enabled",true,"kind",owner.kind().getSerializedName(),"cells",cells);
    }
    static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||core==null)return;
        var mc=Minecraft.getInstance();if(mc.level!=level){core=null;level=null;return;}
        var owner=SanctuaryAssembly.owner(core,mc.level.getBlockState(core));if(owner==null)return;
        var camera=event.getCamera().getPosition();var pose=new PoseStack();
        var source=mc.renderBuffers().bufferSource();var lines=source.getBuffer(RenderType.lines());
        for(var c:SanctuaryShape.cells(owner.kind(),owner.facing())){
            var pos=core.offset(c.offset());var s=mc.level.getBlockState(pos);
            for(var b:s.getCollisionShape(mc.level,pos).toAabbs())
                LevelRenderer.renderLineBox(pose,lines,b.move(pos).inflate(.002).move(-camera.x,-camera.y,-camera.z),.15f,1f,.35f,1f);
        }
        source.endBatch(RenderType.lines());
    }
}
