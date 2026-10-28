package cn.wwl.orimod.gametest;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;

/** One-coordinate write attribution, test jar only and never enabled for timing. */
public final class CreekWaterSourceTrace {
    public static final boolean ENABLED=Boolean.getBoolean("orimod.waterAudit.source")
            &&"creek_water_baseline".equals(System.getProperty("orimod.worldgen.audit.mode"));
    public static final boolean FOREIGN=Boolean.getBoolean("orimod.waterAudit.foreign")
            &&"creek_water_baseline".equals(System.getProperty("orimod.worldgen.audit.mode"));
    private static final ConcurrentLinkedQueue<Map<String,Object>> BEFORE=new ConcurrentLinkedQueue<>();
    private static boolean target(BlockPos p){return (p.getX()==16994||p.getX()==16995)&&p.getZ()==11689;}
    public static void before(cn.wwl.orimod.world.RuinPiece piece,net.minecraft.world.level.WorldGenLevel level,
            net.minecraft.world.level.levelgen.structure.BoundingBox clip){
        if(!FOREIGN)return;
        for(int x=16994;x<=16995;x++){
            if(x<clip.minX()||x>clip.maxX()||11689<clip.minZ()||11689>clip.maxZ())continue;
            var states=new ArrayList<String>();
            for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++)states.add(level.getBlockState(new BlockPos(x,y,11689)).toString());
            BEFORE.add(Map.of("column",List.of(x,11689),"minimumY",level.getMinBuildHeight(),"origin",piece.origin().toString(),"clip",clip.toString(),"states",states));
        }
    }
    public static List<Map<String,Object>> beforeSnapshots(){return List.copyOf(BEFORE);}
    private static final ConcurrentLinkedQueue<Map<String,Object>> WRITES=new ConcurrentLinkedQueue<>();
    public static BlockState write(ChunkAccess chunk,BlockPos pos,BlockState state,boolean moving,Operation<BlockState> original){
        var before=original.call(chunk,pos,state,moving);
        if((ENABLED&&pos.getX()==96&&pos.getY()==35&&pos.getZ()==419)||(FOREIGN&&target(pos))){
            WRITES.add(Map.of("position",List.of(pos.getX(),pos.getY(),pos.getZ()),"before",String.valueOf(before),"after",state.toString(),
                    "thread",Thread.currentThread().getName(),"stack",Arrays.stream(Thread.currentThread().getStackTrace()).map(Object::toString).toList()));
        }
        return before;
    }
    public static List<Map<String,Object>> snapshot(){return List.copyOf(WRITES);}
    private CreekWaterSourceTrace(){}
}
