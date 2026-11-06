package cn.wwl.orimod.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GinsoFloodSurfaceTest {
    @Test void overlappingBoxesProduceOneLayerAndKeepOpenDryExit(){
        var boxes=List.of(new GinsoEscapeLayout.Box(0,90,0,4,100,4),new GinsoEscapeLayout.Box(2,90,2,6,105,6));
        var surface=GinsoFloodSurface.at(boxes,95);assertEquals(28,surface.stream().mapToDouble(r->(r.x1()-r.x0())*(r.z1()-r.z0())).sum());
        for(double x=-1;x<7;x+=.25)for(double z=-1;z<7;z+=.25){double a=x,b=z;long hits=surface.stream().filter(r->a>=r.x0()&&a<r.x1()&&b>=r.z0()&&b<r.z1()).count();assertTrue(hits<=1);assertEquals(boxes.stream().anyMatch(v->v.contains(a,95,b)),hits==1);}
        assertEquals(16,GinsoFloodSurface.at(boxes,102).stream().mapToDouble(r->(r.x1()-r.x0())*(r.z1()-r.z0())).sum());assertTrue(GinsoFloodSurface.at(boxes,105).isEmpty());
    }
    @Test void everyActualFloodContainsItsRenderedSurfaceWithoutSkyLeaks(){
        for(int turn=0;turn<4;turn++){var layout=GinsoEscapeLayoutTest.layout(115117,turn);for(double y=90;y<112;y+=.5)for(var r:GinsoFloodSurface.at(layout.flood(),y))assertTrue(layout.inFlood((r.x0()+r.x1())*.5,y,(r.z0()+r.z1())*.5));}
    }
    @Test void continuousPositionsRotateAroundBlockCentresLikeThePiece(){
        var origin=new BlockPos(5048,71,4312);for(int turn=0;turn<4;turn++)for(var p:List.of(new BlockPos(0,94,1),new BlockPos(-9,101,12),new BlockPos(16,106,-4))){var w=origin.offset(GinsoInterior.rotate(p,turn));var local=GinsoEscape.localPosition(origin,turn,Vec3.atBottomCenterOf(w));assertEquals(new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5),local);}
    }
    @Test void recoveryCodecRoundTripsFailAndResumeSnapshotsWithoutSoulLink(){
        var fail=GinsoEscapeRecovery.recover("minecraft:overworld",new BlockPos(100,165,5),12345);
        var failData=GinsoEscapeRecovery.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,fail).getOrThrow();
        assertEquals(fail,GinsoEscapeRecovery.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,failData).getOrThrow());
        assertFalse(fail.resumable());assertFalse(failData.toString().contains("soul"));
        var piece=new GinsoPiece(new BlockPos(5048,71,4312),115117,0,0,3,2,1);
        var resume=GinsoEscapeRecovery.progress(piece,40);
        resume=new GinsoEscapeRecovery("minecraft:overworld",resume.safe(),false,0,resume.origin(),resume.seed(),resume.entrance(),resume.approach(),resume.shapeVersion(),resume.courseVersion(),resume.escapeVersion(),resume.elapsed());
        var resumeData=GinsoEscapeRecovery.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,resume).getOrThrow();
        assertEquals(resume,GinsoEscapeRecovery.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,resumeData).getOrThrow());
        assertTrue(resume.resumable());assertEquals(40,resume.elapsed());assertFalse(resumeData.toString().contains("soul"));
        // Old four-field saves still decode as non-resumable recovery.
        var legacy=com.google.gson.JsonParser.parseString("{\"dimension\":\"minecraft:overworld\",\"safe\":[100,165,5],\"pending\":true,\"retry_at\":9}");
        var parsed=GinsoEscapeRecovery.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,legacy).getOrThrow();
        assertTrue(parsed.pending());assertFalse(parsed.resumable());assertEquals(0,parsed.elapsed());
    }
}
