package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.RuinPiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in diagnostic attribution only, in the GameTest jar. Never used for timings. */
public final class ForestWaterEvidence {
    public static final boolean ENABLED=Boolean.getBoolean("orimod.forestWaterEvidence")&&"forest_natural".equals(System.getProperty("orimod.worldgen.audit.mode"));
    private static final List<BlockPos> WET=List.of(new BlockPos(12,0,58),new BlockPos(11,0,58),new BlockPos(10,0,58),new BlockPos(9,0,58),new BlockPos(8,0,55),new BlockPos(12,0,57),new BlockPos(11,0,57),new BlockPos(10,0,57),new BlockPos(9,0,57),new BlockPos(8,0,57),new BlockPos(10,0,59),new BlockPos(10,0,56),new BlockPos(9,0,56),new BlockPos(8,0,56),new BlockPos(7,0,56));
    private static final Set<Long> WET_WORLD=new HashSet<>();
    static {for(var p:WET)WET_WORLD.add(BlockPos.asLong(4582-p.getZ(),0,3376+p.getX()));}
    private static final ConcurrentLinkedQueue<Map<String,Object>> WRITES=new ConcurrentLinkedQueue<>();
    private static final List<Map<String,Object>> PHASES=new ArrayList<>();
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private static final ThreadLocal<String> OWNER=new ThreadLocal<>();
    private record OwnWrite(String piece,int x,int y,int z,boolean changed){}
    private static final ConcurrentLinkedQueue<OwnWrite> OWN_WRITES=new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Map<String,Object>> SLICES=new ConcurrentLinkedQueue<>();
    public static void beginOwn(StructurePiece piece,net.minecraft.world.level.ChunkPos chunk){
        if(!ENABLED)return;
        if(OWNER.get()!=null)throw new IllegalStateException("Leaked own-write context");
        var b=piece.getBoundingBox();
        boolean main=piece instanceof RuinPiece r&&r.origin().equals(new BlockPos(8214,63,6870));
        boolean approach=!(piece instanceof RuinPiece)&&b.minX()==8193&&b.maxX()==8197&&b.minZ()==6871&&b.maxZ()==6879;
        if(main||approach){String kind=main?"main":"approach";OWNER.set(kind);
            SLICES.add(Map.of("piece",kind,"chunk",List.of(chunk.x,chunk.z),"bounds",List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())));}
    }
    public static void endOwn(){if(ENABLED)OWNER.remove();}
    public static boolean recording(BlockPos p){return ENABLED&&(OWNER.get()!=null||target(p));}

    public static boolean target(BlockPos p){return ENABLED&&(p.getX()>=8217&&p.getX()<=8228&&p.getZ()>=6812&&p.getZ()<=6822&&p.getY()>=-64&&p.getY()<=80||WET_WORLD.contains(BlockPos.asLong(p.getX(),0,p.getZ())));}
    public static void wrote(String phase,BlockPos p,BlockState before,BlockState requested,BlockState after){
        if(!ENABLED)return;
        String owner=OWNER.get();if(owner!=null)OWN_WRITES.add(new OwnWrite(owner,p.getX(),p.getY(),p.getZ(),before!=after));
        if(!target(p))return;
        var stack=Arrays.stream(Thread.currentThread().getStackTrace()).map(Object::toString).toList();
        var row=new LinkedHashMap<String,Object>();row.put("sequence",SEQUENCE.incrementAndGet());row.put("phase",phase);row.put("position",xyz(p));
        row.put("before",before.toString());row.put("requested",requested.toString());row.put("after",after.toString());row.put("changed",before!=after);
        row.put("ori",stack.stream().anyMatch(s->s.contains("cn.wwl.orimod.world.Ruin")||s.contains("cn.wwl.orimod.world.Forest")));
        row.put("thread",Thread.currentThread().getName());row.put("stack",stack);WRITES.add(row);
    }
    static void phase(ServerLevel l,RuinPiece piece,String name){
        if(!ENABLED)return;int site=piece.origin().getX()==4582?1:piece.origin().getX()==8214?2:-1;if(site<0)return;
        var rows=new ArrayList<Map<String,Object>>();var statuses=new TreeMap<String,String>();
        var columns=new ArrayList<BlockPos>();
        if(site==1)for(var p:WET)columns.add(piece.placement().block(p));
        else for(int x=8217;x<=8228;x++)for(int z=6812;z<=6822;z++)columns.add(new BlockPos(x,0,z));
        int low=l.getMinBuildHeight(),high=site==1?100:80;
        for(var p:columns){var chunk=l.getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.CARVERS,false);
            if(chunk==null)throw new IllegalStateException("Unloaded evidence chunk "+p);
            statuses.put(chunk.getPos().toString(),chunk.getPersistedStatus().toString());
            if(name.equals("CARVERS")&&chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES))throw new IllegalStateException("Evidence already decorated "+p);
            var states=new ArrayList<String>();for(int y=low;y<=high;y++)states.add(chunk.getBlockState(p.atY(y)).toString());
            rows.add(Map.of("column",List.of(p.getX(),p.getZ()),"minY",low,"states",states));
        }
        PHASES.add(Map.of("site",site,"phase",name,"worldTick",l.getGameTime(),"statuses",statuses,"columns",rows));save();
    }
    static void save(){if(!ENABLED)return;try{Files.writeString(Path.of("forest-water-evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("phases",PHASES,"writes",List.copyOf(WRITES),"ownWriteAttempts",List.copyOf(OWN_WRITES),"ownSlices",List.copyOf(SLICES))));}catch(Exception e){throw new IllegalStateException(e);}}
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private ForestWaterEvidence(){}
}
