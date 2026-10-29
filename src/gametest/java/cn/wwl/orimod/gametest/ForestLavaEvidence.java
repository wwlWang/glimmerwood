package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.material.*;
import net.minecraft.world.ticks.ScheduledTick;

/** Read-only, explicitly enabled fluid attribution. No loading, writes, or tick scheduling. */
public final class ForestLavaEvidence {
    public static final boolean ENABLED=Boolean.getBoolean("orimod.forestLavaEvidence")&&"forest_natural".equals(System.getProperty("orimod.worldgen.audit.mode"));
    private static final boolean BUILDING_CASE="116120-site0".equals(System.getProperty("orimod.forestFluidEvidenceCase"));
    private static final boolean SHORE_CASE="116119-sites1-2-4".equals(System.getProperty("orimod.forestFluidEvidenceCase"));
    private static final boolean SITE_TWO_CASE=SHORE_CASE||"116119-sites1-2".equals(System.getProperty("orimod.forestFluidEvidenceCase"));
    private static final boolean WATER_CASE=SITE_TWO_CASE||"116119-site1".equals(System.getProperty("orimod.forestFluidEvidenceCase"));
    private static final boolean TRAIL_CASE="116118-site2".equals(System.getProperty("orimod.forestFluidEvidenceCase"));
    private static final BlockPos ORIGIN=TRAIL_CASE?new BlockPos(8214,63,6870):WATER_CASE?new BlockPos(4262,63,3606):new BlockPos(11206,60,10438);
    private record Scope(BlockPos origin,net.minecraft.world.level.block.Rotation rotation,int minX,int maxX,int minZ,int maxZ,
                         int snapshotMinX,int snapshotMaxX,int snapshotMinZ,int snapshotMaxZ,int snapshotMinY,int snapshotMaxY){
        boolean contains(BlockPos p){return p.getX()>=minX&&p.getX()<=maxX&&p.getZ()>=minZ&&p.getZ()<=maxZ&&p.getY()>=-64&&p.getY()<=319;}
        boolean near(ChunkPos p){return p.x>=(minX>>4)-2&&p.x<=(maxX>>4)+2&&p.z>=(minZ>>4)-2&&p.z<=(maxZ>>4)+2;}
        boolean owns(StructurePiece piece){
            if(piece instanceof RuinPiece r)return r.origin().equals(origin);
            if(!(piece instanceof RuinApproachPiece))return false;
            var expected=new RuinPlacement(origin,rotation).blocks(new BlockPos(17,0,-9),new BlockPos(21,0,-1));
            var b=piece.getBoundingBox();return b.minX()==expected.minX()&&b.maxX()==expected.maxX()&&b.minZ()==expected.minZ()&&b.maxZ()==expected.maxZ();
        }
    }
    // Disjoint read-only windows, never a bounding union spanning the two far-apart sites.
    private static final List<Scope> SCOPES=scopes();
    private static List<Scope> scopes(){
        if("116120-site1".equals(System.getProperty("orimod.forestFluidEvidenceCase")))return List.of(new Scope(
                new BlockPos(3568,66,2688),net.minecraft.world.level.block.Rotation.NONE,
                3540,3600,2660,2720,3558,3578,2678,2698,-64,319));
        if(BUILDING_CASE)return List.of(new Scope(new BlockPos(-1386,62,-778),net.minecraft.world.level.block.Rotation.CLOCKWISE_180,
                -1422,-1398,-790,-766,-1418,-1400,-787,-770,-64,319));
        var base=new Scope(ORIGIN,net.minecraft.world.level.block.Rotation.CLOCKWISE_180,
                TRAIL_CASE?8200:WATER_CASE?4224:11168,TRAIL_CASE?8245:WATER_CASE?4254:11206,
                TRAIL_CASE?6800:WATER_CASE?3593:10364,TRAIL_CASE?6840:WATER_CASE?3625:10410,
                TRAIL_CASE?8215:WATER_CASE?4234:11178,TRAIL_CASE?8230:WATER_CASE?4244:11198,
                TRAIL_CASE?6810:WATER_CASE?3604:10374,TRAIL_CASE?6825:WATER_CASE?3614:10396,
                TRAIL_CASE||WATER_CASE?-64:50,TRAIL_CASE||WATER_CASE?100:85);
        var scopes=new ArrayList<Scope>();scopes.add(base);
        if(SITE_TWO_CASE)scopes.add(new Scope(new BlockPos(8310,60,6736),net.minecraft.world.level.block.Rotation.CLOCKWISE_90,
                8232,8304,6748,6816,8257,8272,6775,6790,-64,128));
        if(SHORE_CASE)scopes.add(new Scope(new BlockPos(16640,65,11808),net.minecraft.world.level.block.Rotation.NONE,
                16640,16658,11856,11876,16645,16653,11862,11870,-64,319));
        return List.copyOf(scopes);
    }
    static final int STOP_AFTER=Integer.getInteger("orimod.forestFluidEvidenceStopAfter",TRAIL_CASE?3:WATER_CASE?2:4);
    private static final ConcurrentLinkedQueue<Map<String,Object>> EVENTS=new ConcurrentLinkedQueue<>(),SNAPSHOTS=new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<List<String>,Integer> STACKS=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockState,String> STATE_TEXT=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockState,Boolean> FULL_COLLISION=new ConcurrentHashMap<>();
    private static final Set<Long> BASELINED_COLUMNS=ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<LevelChunkSection,BlockPos> SECTION_ORIGINS=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final ThreadLocal<Map<Long,Long>> SECTION_WRITES=ThreadLocal.withInitial(HashMap::new);
    private static final AtomicLong IDS=new AtomicLong();
    private static final java.util.concurrent.atomic.AtomicInteger STACK_IDS=new java.util.concurrent.atomic.AtomicInteger();
    private static final ThreadLocal<String> OWNER=new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> FEATURES=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<BlockPos>> FLUID_TICKS=ThreadLocal.withInitial(ArrayDeque::new);
    private record Decoration(WorldGenLevel level,ChunkPos chunk,int step){}
    private static final ThreadLocal<Decoration> DECORATION=new ThreadLocal<>();
    private static volatile ServerLevel serverLevel;
    public static boolean target(BlockPos p){return ENABLED&&SCOPES.stream().anyMatch(s->s.contains(p));}
    public static boolean recording(BlockPos p){return ENABLED&&(OWNER.get()!=null||target(p));}
    static long sequence(){return IDS.get();}
    static void baselineColumn(BlockPos p){if(target(p))BASELINED_COLUMNS.add(p.atY(0).asLong());}
    public static boolean wholeColumn(BlockPos p){return target(p)&&BASELINED_COLUMNS.contains(p.atY(0).asLong());}
    static void baselineChunks(Collection<ChunkAccess> chunks){if(ENABLED)for(var chunk:chunks)if(near(chunk.getPos())){
        for(int i=0;i<chunk.getSectionsCount();i++)SECTION_ORIGINS.put(chunk.getSection(i),new BlockPos(chunk.getPos().getMinBlockX(),chunk.getSectionYFromSectionIndex(i)<<4,chunk.getPos().getMinBlockZ()));
    }}
    public static BlockPos sectionPosition(LevelChunkSection section,int x,int y,int z){
        if(!ENABLED)return null;var base=SECTION_ORIGINS.get(section);if(base==null)return null;
        var at=base.offset(x,y,z);return wholeColumn(at)?at:null;
    }
    public static long sectionSequence(BlockPos p){return ENABLED?SECTION_WRITES.get().getOrDefault(p.asLong(),0L):0;}
    public static void sectionWrite(BlockPos p,BlockState before,BlockState next,BlockState after){
        write("SECTION",p,before,next,after);SECTION_WRITES.get().put(p.asLong(),IDS.get());
    }
    /** A nested section call already records this logical attempt and its complete outer stack. */
    public static void outerWrite(String api,BlockPos p,BlockState before,BlockState next,BlockState after,long sectionBefore){
        if(sectionSequence(p)==sectionBefore)write(api,p,before,next,after);
    }
    private static boolean near(ChunkPos p){return SCOPES.stream().anyMatch(s->s.near(p));}
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private static String stateText(BlockState state){return STATE_TEXT.computeIfAbsent(state,BlockState::toString);}
    private static boolean fullCollision(BlockState state){return FULL_COLLISION.computeIfAbsent(state,s->{
        var block=s.getBlock();
        // Partial/dynamic/water-containing states are conservatively non-solid,
        // including a full-looking double slab: no inference from its item/model.
        if(block.hasDynamicShape()||block instanceof net.minecraft.world.level.block.SlabBlock
                ||block instanceof net.minecraft.world.level.block.StairBlock
                ||block instanceof net.minecraft.world.level.block.WallBlock
                ||block instanceof net.minecraft.world.level.block.FenceBlock
                ||!s.getFluidState().isEmpty())return false;
        return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
    });}
    private static Map<String,Object> event(String type,BlockPos p,boolean stack){
        var r=new LinkedHashMap<String,Object>();r.put("sequence",IDS.incrementAndGet());r.put("type",type);r.put("position",xyz(p));
        r.put("nanoTime",System.nanoTime());r.put("thread",Thread.currentThread().getName());r.put("gameTick",serverLevel==null?-1:serverLevel.getGameTime());
        if(OWNER.get()!=null)r.put("owner",OWNER.get());r.put("features",List.copyOf(FEATURES.get()));
        if(!FLUID_TICKS.get().isEmpty())r.put("activeFluidTicks",FLUID_TICKS.get().stream().map(ForestLavaEvidence::xyz).toList());
        var d=DECORATION.get();if(d!=null){r.put("decorationChunk",List.of(d.chunk.x,d.chunk.z));r.put("decorationStep",d.step);}
        if(stack){var lines=Arrays.stream(Thread.currentThread().getStackTrace()).map(Object::toString).toList();
            r.put("stackId",STACKS.computeIfAbsent(lines,k->STACK_IDS.getAndIncrement()));}
        return r;
    }
    public static void write(String api,BlockPos p,BlockState before,BlockState requested,BlockState after){
        if(!recording(p))return;var r=event("write",p,true);r.put("api",api);r.put("before",stateText(before));r.put("requested",stateText(requested));r.put("after",stateText(after));r.put("changed",before!=after);
        r.put("beforeFullCollision",fullCollision(before));r.put("afterFullCollision",fullCollision(after));EVENTS.add(r);
    }
    public static void piece(WorldGenLevel level,StructurePiece piece,ChunkPos c,boolean begin){
        if(!ENABLED)return;var scope=SCOPES.stream().filter(s->s.owns(piece)).findFirst().orElse(null);if(scope==null)return;
        boolean main=piece instanceof RuinPiece;
        if(begin)OWNER.set(main?"main":"approach");
        var r=event(begin?"postProcess_begin":"postProcess_end",scope.origin,false);r.put("slice",List.of(c.x,c.z));EVENTS.add(r);
        snapshot(scope,level,null,(begin?"BEFORE_":"AFTER_")+"POSTPROCESS_"+(main?"MAIN":"APPROACH"),c);
        if(!begin)OWNER.remove();
    }
    public static void feature(WorldGenLevel level,ConfiguredFeature<?,?> feature,BlockPos p,boolean begin,boolean result){
        if(!ENABLED||!near(new ChunkPos(p)))return;
        if(begin){var key=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getKey(feature);
            FEATURES.get().addLast((key==null?"inline":key.toString())+"/"+BuiltInRegistries.FEATURE.getKey(feature.feature())+"@"+xyz(p));}
        var r=event(begin?"feature_begin":"feature_end",p,false);r.put("result",result);EVENTS.add(r);
        if(!begin)FEATURES.get().removeLast();
    }
    public static void decoration(WorldGenLevel level,ChunkPos c,boolean begin){
        if(!ENABLED||!near(c))return;
        if(begin)DECORATION.set(new Decoration(level,c,-1));
        else{step(99);DECORATION.remove();}
    }
    public static void step(int step){
        if(!ENABLED)return;var d=DECORATION.get();if(d==null||step==d.step)return;
        // A step may be empty: first following nonempty step is its exact completion boundary.
        if(d.step<=1&&step>1)snapshot(d.level,null,"AFTER_LAKES",d.chunk);
        if(d.step<=8&&step>8)snapshot(d.level,null,"AFTER_FLUID_SPRINGS",d.chunk);
        DECORATION.set(new Decoration(d.level,d.chunk,step));
    }
    public static void carvers(WorldGenRegion level,ChunkAccess chunk,boolean begin){
        if(ENABLED&&near(chunk.getPos()))snapshot(level,chunk,begin?"BEFORE_CARVERS":"AFTER_CARVERS_CHUNK",chunk.getPos());
    }
    static void phase(ServerLevel level,RuinPiece piece,String name){
        if(!ENABLED)return;serverLevel=level;var scope=SCOPES.stream().filter(s->s.origin.equals(piece.origin())).findFirst().orElse(null);if(scope==null)return;
        snapshot(scope,level,null,name,null);save();
    }
    private static void snapshot(LevelAccessor level,ChunkAccess only,String phase,ChunkPos center){
        for(var scope:SCOPES)if(center==null||scope.near(center))snapshot(scope,level,only,phase,center);
    }
    private static void snapshot(Scope scope,LevelAccessor level,ChunkAccess only,String phase,ChunkPos center){
        var columns=new ArrayList<Map<String,Object>>();
        for(int x=scope.snapshotMinX;x<=scope.snapshotMaxX;x++)for(int z=scope.snapshotMinZ;z<=scope.snapshotMaxZ;z++){
            ChunkAccess chunk;
            if(only!=null){if(only.getPos().x!=(x>>4)||only.getPos().z!=(z>>4))continue;chunk=only;}
            else if(level instanceof ServerLevel server){chunk=server.getChunkSource().getChunk(x>>4,z>>4,ChunkStatus.CARVERS,false);if(chunk==null)continue;}
            else{if(!level.hasChunk(x>>4,z>>4))continue;chunk=level.getChunk(x>>4,z>>4,ChunkStatus.EMPTY,false);if(chunk==null)continue;}
            int minY=scope.snapshotMinY,maxY=scope.snapshotMaxY;
            var states=new ArrayList<String>();for(int y=minY;y<=maxY;y++)states.add(stateText(chunk.getBlockState(new BlockPos(x,y,z))));
            columns.add(Map.of("column",List.of(x,z),"minY",minY,"status",chunk.getPersistedStatus().toString(),"states",states));
        }
        if(columns.isEmpty())return;var r=event("snapshot",scope.origin,false);r.put("phase",phase);if(center!=null)r.put("center",List.of(center.x,center.z));r.put("columns",columns);SNAPSHOTS.add(r);
    }
    public static void schedule(String phase,ScheduledTick<?> tick,boolean present){
        if(!target(tick.pos()))return;
        var r=event(phase,tick.pos(),true);
        if(tick.type() instanceof Fluid fluid)r.put("fluid",BuiltInRegistries.FLUID.getKey(fluid).toString());
        else if(tick.type() instanceof net.minecraft.world.level.block.Block block)r.put("block",BuiltInRegistries.BLOCK.getKey(block).toString());
        else return;
        r.put("triggerTick",tick.triggerTick());r.put("priority",tick.priority().toString());r.put("alreadyScheduled",present);EVENTS.add(r);
    }
    public static void fluidTick(Level level,BlockPos pos,FluidState state,boolean begin){
        if(!target(pos))return;
        if(begin)FLUID_TICKS.get().addLast(pos.immutable());
        var r=event(begin?"fluid_tick_begin":"fluid_tick_end",pos,true);r.put("gameTick",level.getGameTime());r.put("fluid",state.toString());r.put("block",level.getBlockState(pos).toString());EVENTS.add(r);
        if(!begin)FLUID_TICKS.get().removeLast();
    }
    /** Four causal conditions, plus the full upstream block-tick proof for gravity releases. */
    static Map<String,Object> approvedFlow(BlockPos at){
        if(!ENABLED)return null;
        var stacks=new HashMap<Integer,List<String>>();STACKS.forEach((k,v)->stacks.put(v,k));
        var proof=ForestFluidCausalProof.inspect(List.copyOf(EVENTS),stacks,xyz(at),p->target(new BlockPos(p.get(0),p.get(1),p.get(2))));
        if(!Boolean.TRUE.equals(proof.get("accepted"))){System.out.println("FOREST_FLUID_PROOF_MISSING "+xyz(at)+" "+proof.get("missing"));return null;}
        return proof;
    }
    static Map<String,Object> submergedStep(ForestSubmergedStepProof.Column high,ForestSubmergedStepProof.Column low){
        if(!ENABLED)return null;
        var stacks=new HashMap<Integer,List<String>>();STACKS.forEach((k,v)->stacks.put(v,k));
        var proof=ForestSubmergedStepProof.inspect(List.copyOf(EVENTS),stacks,high,low,p->target(new BlockPos(p.get(0),p.get(1),p.get(2))));
        if(!Boolean.TRUE.equals(proof.get("accepted"))){System.out.println("FOREST_SUBMERGED_PROOF_MISSING "+proof.get("missing"));return null;}
        return proof;
    }
    static void save(){if(!ENABLED)return;try{
        var stacks=new TreeMap<Integer,List<String>>();STACKS.forEach((k,v)->stacks.put(v,k));
        var file=Path.of("forest-lava-evidence.json");var pending=Path.of("forest-lava-evidence.pending.json");
        try(var writer=Files.newBufferedWriter(pending)){
            new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("events",EVENTS.stream().sorted(Comparator.comparingLong(e->((Number)e.get("sequence")).longValue())).toList(),"snapshots",List.copyOf(SNAPSHOTS),"stacks",stacks),writer);
        }
        Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }catch(Exception e){throw new IllegalStateException(e);}}
    private ForestLavaEvidence(){}
}
