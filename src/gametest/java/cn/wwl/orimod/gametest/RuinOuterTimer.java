package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.RuinPiece;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.world.level.ChunkPos;

/** Test jar only. Both timestamp boundaries exclude observer allocation and IO. */
public final class RuinOuterTimer {
    public static final boolean ENABLED=Boolean.getBoolean("orimod.worldgen.audit")
            &&"creek_acceptance".equals(System.getProperty("orimod.worldgen.audit.mode"));
    private static final ThreadLocal<long[]> START=ThreadLocal.withInitial(()->new long[1]);
    private static final ConcurrentLinkedQueue<Slice> ROWS=new ConcurrentLinkedQueue<>();
    public record Slice(String kind,int version,int originX,int originY,int originZ,int chunkX,int chunkZ,long nanos,String thread) {}
    public static void begin(){
        if(!ENABLED)return;
        var start=START.get(); // Lazy allocation is BEFORE the first timestamp.
        start[0]=System.nanoTime();
    }
    public static void end(RuinPiece piece,ChunkPos chunk){
        if(!ENABLED)return;
        long end=System.nanoTime(); // All observer reads/allocations follow this timestamp.
        long elapsed=end-START.get()[0];var p=piece.origin();
        ROWS.add(new Slice(piece.kind().id,piece.layoutVersion(),p.getX(),p.getY(),p.getZ(),chunk.x,chunk.z,elapsed,Thread.currentThread().getName()));
    }
    public static List<Slice> snapshot(){return List.copyOf(ROWS);}
    private RuinOuterTimer(){}
}
