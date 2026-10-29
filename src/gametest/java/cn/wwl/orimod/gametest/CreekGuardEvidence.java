package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Read-only attribution of the five frozen creek discrepancies. Test jar only. */
public final class CreekGuardEvidence {
    public static final boolean FULL=Boolean.getBoolean("orimod.creek.fullEvidence");
    public static final boolean ENABLED=FULL||"creek_guard_evidence".equals(System.getProperty("orimod.worldgen.audit.mode"));
    private static final long START_NANOS=System.nanoTime();
    private static final long START_MILLIS=System.currentTimeMillis();
    private static final List<BlockPos> TARGETS=List.of(new BlockPos(12965,88,9025),new BlockPos(12960,88,9032),new BlockPos(12961,88,9033));
    private static final Set<Long> COLUMNS=new HashSet<>();
    static {for(var p:TARGETS)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)COLUMNS.add(BlockPos.asLong(p.getX()+x,0,p.getZ()+z));}
    private static final ThreadLocal<Deque<String>> FEATURES=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ConcurrentLinkedQueue<Map<String,Object>> EVENTS=new ConcurrentLinkedQueue<>();
    private static final AtomicLong IDS=new AtomicLong();
    private static final List<Map<String,Object>> PHASES=new ArrayList<>();
    public static boolean column(BlockPos p){return ENABLED&&COLUMNS.contains(BlockPos.asLong(p.getX(),0,p.getZ()));}
    public static boolean nearby(BlockPos p){return ENABLED&&p.getX()>=12928&&p.getX()<=12997&&p.getZ()>=8993&&p.getZ()<=9065;}
    public static void feature(WorldGenLevel level,ConfiguredFeature<?,?> feature,BlockPos p,boolean begin,boolean result){
        if(!nearby(p))return;
        if(!begin){if(FULL){var r=row("feature_end",p);r.put("result",result);center(r,level);EVENTS.add(r);}FEATURES.get().removeLast();return;}
        var key=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getKey(feature);
        var type=net.minecraft.core.registries.BuiltInRegistries.FEATURE.getKey(feature.feature());
        FEATURES.get().addLast((key==null?"inline":key.toString())+"/"+type+"@"+xyz(p));
        if(FULL){var r=row("feature_begin",p);center(r,level);EVENTS.add(r);}
    }
    private static Map<String,Object> row(String type,BlockPos p){
        var r=new LinkedHashMap<String,Object>();r.put("sequence",IDS.incrementAndGet());r.put("type",type);r.put("position",xyz(p));
        r.put("nanosSinceStart",System.nanoTime()-START_NANOS);r.put("features",List.copyOf(FEATURES.get()));r.put("thread",Thread.currentThread().getName());
        r.put("stack",Arrays.stream(Thread.currentThread().getStackTrace()).map(Object::toString).toList());return r;
    }
    public static void wrote(ChunkPos center,BlockPos p,BlockState before,BlockState requested,BlockState after){
        if(!column(p))return;var r=row("write",p);r.put("center",List.of(center.x,center.z));
        r.put("before",before.toString());r.put("requested",requested.toString());r.put("after",after.toString());EVENTS.add(r);
    }
    public static void decision(String kind,WorldGenLevel level,BlockPos p,int margin,boolean denied){
        if(!nearby(p))return;var r=row("guard",p);r.put("guard",kind);r.put("margin",margin);r.put("denied",denied);
        if(level instanceof WorldGenRegion region)r.put("center",List.of(region.getCenter().x,region.getCenter().z));
        EVENTS.add(r);
    }
    private static void center(Map<String,Object> r,LevelAccessor level){
        if(level instanceof WorldGenRegion region)r.put("center",List.of(region.getCenter().x,region.getCenter().z));
    }
    public static boolean mushroomPatch(BlockPos p){return FULL&&column(p)&&FEATURES.get().stream().anyMatch(f->f.contains("brown_mushroom"));}
    public static void mushroom(String type,LevelReader level,BlockPos p,boolean result){
        if(!FULL||!column(p))return;var r=row(type,p);r.put("result",result);
        r.put("block",level.getBlockState(p).toString());r.put("below",level.getBlockState(p.below()).toString());
        r.put("rawBrightness",level.getRawBrightness(p,0));
        if(level instanceof LevelAccessor accessor)center(r,accessor);EVENTS.add(r);
    }
    public static void decoration(WorldGenLevel level,ChunkPos c,boolean begin){
        if(!FULL||!nearby(c.getMiddleBlockPosition(88)))return;
        var r=row(begin?"decoration_begin":"decoration_end",c.getMiddleBlockPosition(88));center(r,level);r.put("chunk",List.of(c.x,c.z));EVENTS.add(r);
    }
    public static void piece(WorldGenLevel level,RuinPiece piece,ChunkPos c,boolean begin){
        if(!FULL||!nearby(c.getMiddleBlockPosition(88)))return;
        var r=row(begin?"postProcess_begin":"postProcess_end",piece.origin());center(r,level);r.put("chunk",List.of(c.x,c.z));r.put("kind",piece.kind().id);EVENTS.add(r);
    }
    static void checkpoint(ServerLevel l,RuinPiece p,String phase){
        if(FULL&&p.origin().equals(new BlockPos(12960,78,9068)))snapshot(l,phase);
    }
    static void finishFull(){
        if(!FULL)return;var report=new LinkedHashMap<String,Object>();report.put("protocol","unaltered creek_acceptance full five rounds plus read-only observer");
        report.put("startEpochMillis",START_MILLIS);report.put("phases",PHASES);
        report.put("events",EVENTS.stream().sorted(Comparator.comparingLong(r->((Number)r.get("sequence")).longValue())).toList());
        try{Files.writeString(Path.of("creek-full-evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void snapshot(ServerLevel l,String phase){
        var rows=new ArrayList<Map<String,Object>>();
        for(long c:COLUMNS){var p=BlockPos.of(c);var states=new ArrayList<String>();
            var chunk=l.getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.CARVERS,false);
            if(chunk==null)throw new IllegalStateException("Missing observed column "+p);
            for(int y=l.getMinBuildHeight();y<l.getMaxBuildHeight();y++)states.add(chunk.getBlockState(p.atY(y)).toString());
            rows.add(Map.of("column",List.of(p.getX(),p.getZ()),"minY",l.getMinBuildHeight(),"status",chunk.getPersistedStatus().toString(),"states",states));}
        PHASES.add(Map.of("phase",phase,"columns",rows));
    }
    static void started(ServerStartedEvent event){
        var server=event.getServer();var l=server.overworld();var report=new LinkedHashMap<String,Object>();report.put("status","running");
        var sites=new ArrayList<Map<String,Object>>();report.put("sites",sites);
        try{
            if(l.getSeed()!=116118)throw new IllegalStateException("Wrong evidence seed");
            // Reproduce the original acceptance's locate/load order up to the target.
            for(int round=0;round<=3;round++)for(var kind:new RuinKind[]{RuinKind.CREEK,RuinKind.FOREST,RuinKind.CAVE}){
                if(round==3&&kind!=RuinKind.CREEK)continue;
                var holder=l.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,kind.location()));
                var from=round==0?(kind==RuinKind.CREEK?l.getSharedSpawnPos():new BlockPos(0,110,128)):new BlockPos(round*4096,110,round*3072);
                var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),from,100,false);
                var cp=new ChunkPos(Objects.requireNonNull(found).getFirst());
                var start=l.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
                var piece=(RuinPiece)start.getPieces().stream().filter(RuinPiece.class::isInstance).findFirst().orElseThrow();
                var b=piece.getBoundingBox();var chunks=new ArrayList<ChunkPos>();
                for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++)chunks.add(new ChunkPos(x,z));
                chunks.sort(Comparator.comparingInt(p->Math.max(Math.abs(p.x-cp.x),Math.abs(p.z-cp.z))));
                // Same CARVERS pre-load order as CreekNaturalBaseline, no mutating reads.
                if(kind==RuinKind.CREEK)for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++)l.getChunk(x,z,ChunkStatus.CARVERS,true);
                boolean target=round==3&&kind==RuinKind.CREEK;
                if(target){if(!piece.origin().equals(new BlockPos(12960,78,9068)))throw new IllegalStateException("Target changed "+piece.origin());snapshot(l,"CARVERS");}
                for(var chunk:chunks)l.getChunk(chunk.x,chunk.z,ChunkStatus.FULL,true);
                if(target)snapshot(l,"AFTER_FULL");
                sites.add(Map.of("round",round,"kind",kind.id,"origin",xyz(piece.origin()),"rotation",piece.getRotation().name()));
                CreekFixtureChunks.settle(l,"guard-evidence-site");
                if(target)snapshot(l,"AFTER_SETTLE");
                for(int i=0;i<4;i++){l.getChunkSource().tick(()->true,false);CreekFixtureChunks.settle(l,"guard-evidence-unload");}
                System.out.println("CREEK_GUARD_EVIDENCE round="+round+" kind="+kind.id+" events="+EVENTS.size());
            }
            report.put("status","complete");
        }catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{report.put("phases",PHASES);report.put("events",EVENTS.stream().sorted(Comparator.comparingLong(r->((Number)r.get("sequence")).longValue())).toList());
            try{Files.writeString(Path.of("creek-guard-evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}finally{server.halt(false);}}
    }
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private CreekGuardEvidence(){}
}
