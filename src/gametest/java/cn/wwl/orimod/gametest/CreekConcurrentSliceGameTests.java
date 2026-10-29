package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.RuinKind;
import cn.wwl.orimod.world.RuinApproachPiece;
import cn.wwl.orimod.world.RuinSiteRules;
import cn.wwl.orimod.world.RuinPiece;
import cn.wwl.orimod.world.RuinPlacement;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.*;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrent piece/input-list regression, using a thread-safe in-memory backend.
 * Workers NEVER call ServerLevel writes, entity creation, ticks, or chunk access.
 * Neighbour finalization is deliberately deferred and then run on the test thread.
 * CreekSliceEquivalenceGameTests separately covers vanilla's live neighbour/NBT/
 * fluid behaviour against the original placement implementation.
 */
@GameTestHolder("orimod_creek_rebuild")
public final class CreekConcurrentSliceGameTests {
    private static final long SEED=0x434f4e4355525245L;
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(var r:Rotation.values())out.add(new TestFunction("creek_concurrent","creek_concurrent.same_piece_"+r.name().toLowerCase(),
                "orimod_creek_rebuild:room",Rotation.NONE,900,0,true,h->concurrent(h,r)));
        return out;
    }
    @GameTestGenerator public static Collection<TestFunction> forestCases(){
        var out=new ArrayList<TestFunction>();for(var r:Rotation.values())out.add(new TestFunction("forest_kernel","forest_kernel.concurrent_"+r.name().toLowerCase(),
                "orimod_forest_red:room",Rotation.NONE,900,0,true,h->concurrent(h,r,RuinKind.FOREST)));return out;
    }
    private static RuinPiece piece(GameTestHelper h,Rotation rotation,RuinKind kind){return piece(h,rotation,kind,false);}
    private static RuinPiece piece(GameTestHelper h,Rotation rotation,RuinKind kind,boolean approach){
        var placement=RuinPlacement.atMinimum(kind.layout(1),new BlockPos(135,120,-181),rotation);
        var natural=new int[kind==RuinKind.CREEK?85*107:79*95];Arrays.fill(natural,8); // Memory.naturalY = origin + 8.
        int[] route=new int[approach?45:0];Arrays.fill(route,placement.origin().getY()+9);
        var initial=new RuinPiece(h.getLevel().getStructureManager(),kind,placement,1,route,natural,kind==RuinKind.FOREST?1:0);
        var tag=initial.createTag(StructurePieceSerializationContext.fromLevel(h.getLevel()));
        // No real mobs may be constructed on a worker. Pre-existing receipts are
        // the normal production path for a piece whose guards already spawned.
        tag.putLongArray("OriBeetlesSpawned",initial.beetles().stream().mapToLong(BlockPos::asLong).toArray());
        var piece=new RuinPiece(h.getLevel().getStructureManager(),tag);
        piece.placeSettings().setKnownShape(true);
        piece.placeSettings().setBoundingBox(new BoundingBox(-13,-12,-11,-10,-9,-8));
        return piece;
    }
    private static void concurrent(GameTestHelper h,Rotation rotation){concurrent(h,rotation,RuinKind.CREEK);}
    private static void concurrent(GameTestHelper h,Rotation rotation,RuinKind kind){
        var shared=piece(h,rotation,kind);var guard=new Guard(shared);var chunks=chunks(shared);
        // Template-local index is prepared in the constructor, before slice writes.
        // First selection still runs concurrently on the same untouched piece.
        var cold=new Memory(h.getLevel(),shared,guard);run(h,shared,cold,chunks,true,0);cold.converge();
        var reference=piece(h,rotation,kind);var serial=new Memory(h.getLevel(),reference,new Guard(reference));
        run(h,reference,serial,chunks,false,0);serial.converge();var expected=serial.snapshot();
        compare(h,expected,cold.snapshot(),rotation,0,cold);
        for(int round=1;round<3;round++){
            var memory=new Memory(h.getLevel(),shared,guard);run(h,shared,memory,chunks,true,round);memory.converge();
            compare(h,expected,memory.snapshot(),rotation,round,memory);
        }
        guard.check();h.succeed();
    }
    static void forestApproach(GameTestHelper h,Rotation rotation){
        Snapshot expected=null;
        for(int variant=0;variant<4;variant++){
            var piece=piece(h,rotation,RuinKind.FOREST,true);var memory=new Memory(h.getLevel(),piece,new Guard(piece));memory.approachCaves=true;
            int[] samples=new int[45];Arrays.fill(samples,piece.origin().getY()+9);
            var approach=new RuinApproachPiece(RuinApproachPiece.doorway(RuinKind.FOREST,piece.layout(),piece.placement()),samples,false,true,piece.forestApproachSideProfile());
            var order=chunks(piece);var ab=approach.getBoundingBox();
            for(int cx=ab.minX()>>4;cx<=ab.maxX()>>4;cx++)for(int cz=ab.minZ()>>4;cz<=ab.maxZ()>>4;cz++){var cp=new ChunkPos(cx,cz);if(!order.contains(cp))order.add(cp);}
            if(variant%2==1)Collections.reverse(order);
            for(int repeat=0;repeat<3;repeat++){
                runPair(h,piece,approach,memory,order,variant>=2,variant%2==1);
                memory.converge();var actual=memory.snapshot();
                if(expected==null)expected=actual;else compare(h,expected,actual,rotation,variant*3+repeat,memory);
                // Check both deep sides and the three-column approach axis.
                for(int x:new int[]{17,21})for(int z=-5;z<=-1;z++){
                    int floor=RuinSiteRules.stepFloor(piece.origin().getY()+9,piece.origin().getY()+piece.layout().approachY(),z+8);
                    var at=piece.placement().block(new BlockPos(x,0,z)).atY(floor+1);
                    h.assertTrue(approach.ownsGuard(at,memory.state(at)),"Missing combined cave edge "+at+" variant="+variant);
                    for(int xx=18;xx<=20;xx++)for(int dy=1;dy<=2;dy++){
                        var axis=piece.placement().block(new BlockPos(xx,0,z)).atY(floor+dy);
                        h.assertTrue(memory.state(axis).getCollisionShape(memory.level,axis).isEmpty(),"Combined central axis blocked "+axis);
                    }
                }
                for(int x:new int[]{17,21})for(int z=-8;z<=-6;z++){
                    int floor=RuinSiteRules.stepFloor(piece.origin().getY()+9,piece.origin().getY()+piece.layout().approachY(),z+8);
                    var at=piece.placement().block(new BlockPos(x,0,z)).atY(floor+1);
                    h.assertFalse(approach.ownsGuard(at,memory.state(at)),"Ordinary pre-fill flank gained unnecessary wall "+at);
                }
                var context=StructurePieceSerializationContext.fromLevel(h.getLevel());
                piece=new RuinPiece(h.getLevel().getStructureManager(),piece.createTag(context));
                piece.placeSettings().setKnownShape(true);
                piece.placeSettings().setBoundingBox(new BoundingBox(-13,-12,-11,-10,-9,-8));
                approach=new RuinApproachPiece(approach.createTag(context));
            }
        }
        h.succeed();
    }
    static void forestFluids(GameTestHelper h,Rotation rotation,boolean lava){
        Snapshot expected=null;
        for(int variant=0;variant<4;variant++){
            var piece=piece(h,rotation,RuinKind.FOREST,true);
            var context=StructurePieceSerializationContext.fromLevel(h.getLevel());
            var tag=piece.createTag(context);var noise=new int[79*95];Arrays.fill(noise,1);tag.putIntArray("ForestNaturalTerrain",noise);
            piece=new RuinPiece(h.getLevel().getStructureManager(),tag);piece.placeSettings().setKnownShape(true);
            var memory=new Memory(h.getLevel(),piece,new Guard(piece));memory.fluidCase=true;memory.fluidLava=lava;
            int[] samples=new int[45];Arrays.fill(samples,piece.origin().getY()+9);
            var approach=new RuinApproachPiece(RuinApproachPiece.doorway(RuinKind.FOREST,piece.layout(),piece.placement()),samples,false,true,piece.forestApproachSideProfile());
            var order=chunks(piece);if(variant%2==1)Collections.reverse(order);
            for(int repeat=0;repeat<3;repeat++){
                runPair(h,piece,approach,memory,order,variant>=2,variant%2==1);
                memory.converge();var actual=memory.snapshot();
                if(expected==null)expected=actual;else compare(h,expected,actual,rotation,variant*3+repeat,memory);
                for(var q:ForestFluidBoundaryGameTests.LAKE){
                    var at=piece.placement().block(q);h.assertTrue(memory.state(at).is(lava?Blocks.LAVA:Blocks.WATER),"Replay filled lake "+at);
                    for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.DOWN))if(!ForestFluidBoundaryGameTests.LAKE.contains(q.relative(d)))
                        h.assertTrue(memory.state(piece.placement().block(q.relative(d))).isCollisionShapeFullBlock(memory.level,piece.placement().block(q.relative(d))),"Replay opened lake wall "+q.relative(d));
                }
                ForestFluidBoundaryGameTests.checkEdges(h,piece,memory.level);
                piece=new RuinPiece(h.getLevel().getStructureManager(),piece.createTag(context));piece.placeSettings().setKnownShape(true);
                approach=new RuinApproachPiece(approach.createTag(context));
            }
        }
        h.succeed();
    }
    private static void runPair(GameTestHelper h,RuinPiece piece,RuinApproachPiece approach,Memory memory,List<ChunkPos> order,boolean parallel,boolean approachFirst){
        var executor=parallel?Executors.newFixedThreadPool(4):null;var tasks=new ArrayList<Future<?>>();
        try{
            for(var chunk:order){Runnable action=()->{
                var clip=new BoundingBox(chunk.getMinBlockX(),-64,chunk.getMinBlockZ(),chunk.getMaxBlockX(),319,chunk.getMaxBlockZ());
                memory.currentClip.set(clip);int active=memory.active.incrementAndGet();memory.maxActive.accumulateAndGet(active,Math::max);
                try{
                    if(approachFirst)approach.postProcess(memory.level,memory.structures,memory.generator,RandomSource.create(SEED^chunk.toLong()),clip,chunk,BlockPos.ZERO);
                    piece.postProcess(memory.level,memory.structures,memory.generator,RandomSource.create(SEED^chunk.toLong()),clip,chunk,BlockPos.ZERO);
                    if(!approachFirst)approach.postProcess(memory.level,memory.structures,memory.generator,RandomSource.create(SEED^chunk.toLong()),clip,chunk,BlockPos.ZERO);
                    memory.completed.add(chunk.toLong());
                }finally{memory.active.decrementAndGet();memory.currentClip.remove();}
            };if(parallel)tasks.add(executor.submit(action));else action.run();}
            for(var task:tasks)task.get(60,TimeUnit.SECONDS);
        }catch(Exception e){throw new AssertionError("Combined forest slice failed "+piece.getRotation(),e);}
        finally{if(executor!=null){executor.shutdownNow();try{if(!executor.awaitTermination(10,TimeUnit.SECONDS))throw new AssertionError("Combined workers did not finish");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}}
        if(parallel)h.assertTrue(memory.maxActive.get()>=2,"Combined fixture not concurrent");
    }

    private static List<ChunkPos> chunks(RuinPiece piece){
        var bounds=piece.getBoundingBox();var template=piece.template().getBoundingBox(piece.placeSettings(),piece.origin());
        var chunks=new ArrayList<ChunkPos>();
        for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)chunks.add(new ChunkPos(x,z));
        // The first four worker jobs must all contend for a first slice selection,
        // rather than completing only empty terrain-skirt slices.
        chunks.sort(Comparator.comparingInt((ChunkPos p)->template.intersects(p.getMinBlockX(),p.getMinBlockZ(),p.getMaxBlockX(),p.getMaxBlockZ())?0:1)
                .thenComparingInt(p->p.x).thenComparingInt(p->p.z));
        return chunks;
    }
    private static void run(GameTestHelper h,RuinPiece piece,Memory memory,List<ChunkPos> chunks,boolean parallel,int round){
        var order=new ArrayList<>(chunks);
        if(round>0)Collections.shuffle(order,new Random(SEED+round));
        if(!parallel){for(var chunk:order)place(h,piece,memory,chunk);return;}
        var executor=Executors.newFixedThreadPool(4,r->{var t=new Thread(r,"ori-creek-memory-slice");t.setDaemon(true);return t;});
        var first=new CountDownLatch(4);var submitted=new AtomicInteger();var futures=new ArrayList<Future<?>>();
        try{
            for(var chunk:order)futures.add(executor.submit(()->{
                int index=submitted.getAndIncrement();
                if(index<4){first.countDown();try{if(!first.await(10,TimeUnit.SECONDS))throw new AssertionError("Four workers did not start");}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
                place(h,piece,memory,chunk);
            }));
            for(var future:futures)future.get(45,TimeUnit.SECONDS);
        }catch(Exception e){throw new AssertionError("Concurrent memory slice failure, rotation="+piece.getRotation()+" round="+round,e);}
        finally{
            executor.shutdownNow();try{if(!executor.awaitTermination(10,TimeUnit.SECONDS))throw new AssertionError("Memory slice workers did not stop");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        }
        h.assertTrue(memory.maxActive.get()>=2,"Concurrent fixture accidentally ran serially");
    }
    private static void place(GameTestHelper h,RuinPiece piece,Memory memory,ChunkPos chunk){
        var clip=new BoundingBox(chunk.getMinBlockX(),-64,chunk.getMinBlockZ(),chunk.getMaxBlockX(),319,chunk.getMaxBlockZ());
        memory.guard.check();memory.currentClip.set(clip);int active=memory.active.incrementAndGet();memory.maxActive.accumulateAndGet(active,Math::max);
        try{
            piece.postProcess(memory.level,memory.structures,memory.generator,
                    RandomSource.create(SEED^chunk.toLong()),clip,chunk,BlockPos.ZERO);
            memory.completed.add(chunk.toLong());memory.guard.check();
        }finally{memory.active.decrementAndGet();memory.currentClip.remove();}
    }

    private static final class Guard {
        final RuinPiece piece;final BoundingBox box,clip;final int[] coordinates;
        Guard(RuinPiece piece){this.piece=piece;box=piece.getBoundingBox();clip=piece.placeSettings().getBoundingBox();coordinates=coords(box);}
        void check(){
            if(piece.getBoundingBox()!=box||!Arrays.equals(coordinates,coords(piece.getBoundingBox())))throw new AssertionError("Shared piece boundingBox mutated");
            if(piece.placeSettings().getBoundingBox()!=clip)throw new AssertionError("Shared piece settings clip mutated");
            if(!piece.placeSettings().getKnownShape())throw new AssertionError("Shared knownShape changed");
        }
        private static int[] coords(BoundingBox b){return new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()};}
    }
    private record TickKey(BlockPos pos,Object type) {}
    private static final class TickQueue<T> implements LevelTickAccess<T>{
        final ConcurrentHashMap<TickKey,ScheduledTick<T>> ticks=new ConcurrentHashMap<>();
        @Override public void schedule(ScheduledTick<T> tick){ticks.putIfAbsent(new TickKey(tick.pos().immutable(),tick.type()),tick);}
        @Override public boolean hasScheduledTick(BlockPos pos,T type){return ticks.containsKey(new TickKey(pos,type));}
        @Override public boolean willTickThisTick(BlockPos pos,T type){return false;}
        @Override public int count(){return ticks.size();}
    }
    private record TickImage(BlockPos pos,String type,long trigger,String priority) {}
    private record Snapshot(Map<BlockPos,BlockState> states,Map<BlockPos,CompoundTag> entities,
                            Map<Long,List<TickImage>> fluids,Map<Long,List<TickImage>> blocks,Set<Long> completed) {}

    private static final class Memory implements InvocationHandler{
        final RuinPiece piece;final Guard guard;final RegistryAccess registries;
        final StructureManager structures;final net.minecraft.world.level.chunk.ChunkGenerator generator;
        final net.minecraft.world.level.dimension.DimensionType dimension;
        final net.minecraft.world.flag.FeatureFlagSet features;
        final long gameTime;final int naturalY;final WorldGenLevel level;
        final ConcurrentHashMap<BlockPos,BlockState> states=new ConcurrentHashMap<>();
        final ConcurrentHashMap<BlockPos,BlockEntity> entities=new ConcurrentHashMap<>();
        final Set<BlockPos> touched=ConcurrentHashMap.newKeySet();final Set<Long> completed=ConcurrentHashMap.newKeySet();
        final TickQueue<Fluid> fluids=new TickQueue<>();final TickQueue<Block> blocks=new TickQueue<>();
        final AtomicLong subticks=new AtomicLong();final ThreadLocal<BoundingBox> currentClip=new ThreadLocal<>();
        final Set<Long> touchedColumns=ConcurrentHashMap.newKeySet();
        final AtomicInteger active=new AtomicInteger(),maxActive=new AtomicInteger();
        int convergencePasses;boolean approachCaves,fluidCase,fluidLava;
        Memory(ServerLevel real,RuinPiece piece,Guard guard){
            this.piece=piece;this.guard=guard;structures=real.structureManager();generator=real.getChunkSource().getGenerator();registries=real.registryAccess();dimension=real.dimensionType();features=real.enabledFeatures();gameTime=real.getGameTime();naturalY=piece.origin().getY()+8;
            level=(WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},this);
        }
        private BlockState natural(BlockPos pos){
            int dx=pos.getX()-piece.origin().getX(),dz=pos.getZ()-piece.origin().getZ();
            int x=switch(piece.getRotation()){case NONE->dx;case CLOCKWISE_90->dz;case CLOCKWISE_180->-dx;case COUNTERCLOCKWISE_90->-dz;};
            int z=switch(piece.getRotation()){case NONE->dz;case CLOCKWISE_90->-dx;case CLOCKWISE_180->-dz;case COUNTERCLOCKWISE_90->dx;};
            int y=pos.getY()-piece.origin().getY();
            if(fluidCase){
                var q=new BlockPos(x,y,z);
                if(ForestFluidBoundaryGameTests.LAKE.contains(q)){var fluid=(fluidLava?Blocks.LAVA:Blocks.WATER).defaultBlockState();return q.equals(ForestFluidBoundaryGameTests.LAKE.getLast())?fluid.setValue(LiquidBlock.LEVEL,fluidLava?2:1):fluid;}
                for(var d:Direction.values())if(d!=Direction.UP&&ForestFluidBoundaryGameTests.LAKE.contains(q.relative(d.getOpposite())))return Blocks.STONE.defaultBlockState();
                int top=x>=14&&x<=23&&z>=49&&z<=60?5:1;
                return (y<top?Blocks.DIRT:y==top?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState();
            }
            // Continuous flank caves plus ordinary low soil that the skirt fills.
            if(approachCaves&&(x==16||x==22)&&z>=-9&&z<=-1)return (y<=(z>=-5?-8:4)?Blocks.STONE:Blocks.AIR).defaultBlockState();
            if(x>=-4&&x<=-2&&z>=25&&z<=28)return (y<=-8?Blocks.STONE:Blocks.AIR).defaultBlockState();
            if(x>=-4&&x<=-2&&z>=35&&z<=38&&y>=3&&y<=10)return Blocks.BIRCH_PLANKS.defaultBlockState();
            if(x>=-4&&x<=-2&&z>=35&&z<=38&&y>=11&&y<=12)return (y==12?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState();
            int top=x>=-4&&x<=-2&&z>=40&&z<=43?naturalY+4:naturalY;
            return (pos.getY()<top?Blocks.DIRT:pos.getY()==top?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState();
        }
        private BlockState state(BlockPos pos){return states.getOrDefault(pos,natural(pos));}
        private boolean set(BlockPos mutable,BlockState state){
            guard.check();var pos=mutable.immutable();var clip=currentClip.get();
            if(clip!=null&&!clip.isInside(pos))throw new AssertionError("Worker writes outside its own clip: "+pos+" clip="+clip);
            touched.add(pos);touchedColumns.add(pos.atY(0).asLong());var before=state(pos);if(before==state)return false;
            if(state==natural(pos))states.remove(pos);else states.put(pos,state);
            if(before.getBlock()!=state.getBlock())entities.remove(pos);
            else {var be=entities.get(pos);if(be!=null)be.setBlockState(state);}
            return true;
        }
        private BlockEntity entity(BlockPos mutable){
            var pos=mutable.immutable();var state=state(pos);if(!state.hasBlockEntity())return null;
            return entities.computeIfAbsent(pos,p->((EntityBlock)state.getBlock()).newBlockEntity(p,state));
        }
        @Override public Object invoke(Object proxy,Method method,Object[] values)throws Throwable{
            Object[] a=values==null?new Object[0]:values;
            switch(method.getName()){
                case "getBlockState":return state((BlockPos)a[0]);
                case "getFluidState":return state((BlockPos)a[0]).getFluidState();
                case "setBlock":return set((BlockPos)a[0],(BlockState)a[1]);
                case "getBlockEntity":if(a.length==1)return entity((BlockPos)a[0]);break;
                case "registryAccess":return registries;
                case "dimensionType":return dimension;
                case "enabledFeatures":return features;
                case "isClientSide":return false;
                case "getSeed":return SEED;
                case "getMinBuildHeight":return -64;
                case "getHeight":{
                    if(a.length==0)return 384;
                    if(a.length==3){
                        var at=new BlockPos((int)a[1],naturalY,(int)a[2]);var clip=currentClip.get();
                        if(touchedColumns.contains(at.atY(0).asLong())&&(clip==null||!clip.isInside(at)))throw new AssertionError("Seam reads a column another slice already wrote");
                        // The synthetic natural() includes +4 outcrops and deep
                        // cave columns. Its pre-write heightmap must describe those,
                        // not claim a flat naturalY until the first write.
                        for(int y=319;y>=-64;y--)if(!state(at.atY(y)).isAir())return y+1;
                        return -64;
                    }
                    break;
                }
                case "getGameTime", "dayTime":return gameTime;
                case "getSeaLevel":return 63;
                case "getSkyDarken":return 0;
                case "getRawBrightness", "getBrightness":return 15;
                case "hasChunk", "ensureCanWrite":return true;
                case "getBlockTicks":return blocks;
                case "getFluidTicks":return fluids;
                case "nextSubTickCount":return subticks.getAndIncrement();
                case "scheduleTick":{
                    var pos=((BlockPos)a[0]).immutable();int delay=(int)a[2];var priority=a.length==4?(TickPriority)a[3]:TickPriority.NORMAL;
                    long sequence=subticks.getAndIncrement();
                    if(a[1] instanceof Fluid fluid)fluids.schedule(new ScheduledTick<>(fluid,pos,gameTime+delay,priority,sequence));
                    else blocks.schedule(new ScheduledTick<>((Block)a[1],pos,gameTime+delay,priority,sequence));return null;
                }
                case "blockUpdated", "setCurrentlyGenerating", "levelEvent", "gameEvent", "playSound", "addParticle":return null;
                case "toString":return "CreekConcurrentMemoryWorld";
                case "hashCode":return System.identityHashCode(proxy);
                case "equals":return proxy==a[0];
                // Intentionally no delegation to ServerLevel. A new dependency
                // must be modelled explicitly instead of silently touching it.
                case "getLevel", "getChunk", "getServer", "getChunkSource", "addFreshEntity", "addFreshEntityWithPassengers":
                    throw new AssertionError("Memory backend must not access real world: "+method);
                default:break;
            }
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,a);
            throw new AssertionError("Unmodelled memory-world read/write: "+method);
        }
        void converge(){
            if(active.get()!=0||currentClip.get()!=null)throw new AssertionError("Neighbour convergence started while a worker remained");
            var candidates=new HashSet<BlockPos>(touched);for(var p:touched)for(var d:Direction.values())candidates.add(p.relative(d));
            var ordered=new ArrayList<>(candidates);ordered.sort(Comparator.comparingInt((BlockPos p)->p.getY()).thenComparingInt(p->p.getX()).thenComparingInt(p->p.getZ()));
            for(int pass=0;pass<8;pass++){
                int changed=0;for(var p:ordered){var before=state(p);var after=Block.updateFromNeighbourShapes(before,level,p);if(before!=after){set(p,after);changed++;}}
                convergencePasses=pass+1;if(changed==0)return;
            }
            throw new AssertionError("Memory neighbour states did not converge after eight passes");
        }
        Snapshot snapshot(){
            var allEntities=new HashMap<BlockPos,CompoundTag>();
            for(var p:states.keySet())if(state(p).hasBlockEntity()){
                var be=entity(p);if(be!=null){if(be.getLevel()!=null)throw new AssertionError("Memory BE unexpectedly has real Level");allEntities.put(p,be.saveWithFullMetadata(registries));}
            }
            return new Snapshot(Map.copyOf(states),Map.copyOf(allEntities),tickImages(fluids),tickImages(blocks),Set.copyOf(completed));
        }
        private <T> Map<Long,List<TickImage>> tickImages(TickQueue<T> queue){
            var groups=new TreeMap<Long,List<ScheduledTick<T>>>();for(var t:queue.ticks.values())groups.computeIfAbsent(ChunkPos.asLong(t.pos()),k->new ArrayList<>()).add(t);
            var out=new TreeMap<Long,List<TickImage>>();
            groups.forEach((chunk,ticks)->{
                ticks.sort(Comparator.comparingLong((ScheduledTick<T> t)->t.triggerTick()).thenComparingInt(t->t.priority().ordinal()).thenComparingLong(t->t.subTickOrder()));
                out.put(chunk,ticks.stream().map(t->new TickImage(t.pos(),t.type() instanceof Fluid f?BuiltInRegistries.FLUID.getKey(f).toString():BuiltInRegistries.BLOCK.getKey((Block)t.type()).toString(),t.triggerTick()-gameTime,t.priority().name())).toList());
            });return Map.copyOf(out);
        }
    }
    private static void compare(GameTestHelper h,Snapshot expected,Snapshot actual,Rotation rotation,int round,Memory memory){
        var differences=new ArrayList<String>();int count=0;
        var positions=new HashSet<>(expected.states.keySet());positions.addAll(actual.states.keySet());
        for(var p:positions)if(expected.states.get(p)!=actual.states.get(p)){if(differences.size()<12)differences.add("state "+p+" serial="+expected.states.get(p)+" parallel="+actual.states.get(p));count++;}
        var bePositions=new HashSet<>(expected.entities.keySet());bePositions.addAll(actual.entities.keySet());
        for(var p:bePositions)if(!Objects.equals(expected.entities.get(p),actual.entities.get(p))){if(differences.size()<12)differences.add("BE "+p+" serial="+expected.entities.get(p)+" parallel="+actual.entities.get(p));count++;}
        count+=tickDifferences("fluid",expected.fluids,actual.fluids,differences);
        count+=tickDifferences("block",expected.blocks,actual.blocks,differences);
        if(!expected.completed.equals(actual.completed)){if(differences.size()<12)differences.add("completed chunks serial="+expected.completed+" parallel="+actual.completed);count++;}
        System.out.println("CREEK_CONCURRENT rotation="+rotation+" round="+round+" cache="+(round==0?"prepared_index_first_selection":"reused_index")+" backend=memory_deferred_neighbours"+
                " chunks="+actual.completed.size()+" stateCells="+positions.size()+" BEs="+actual.entities.size()+" fluidTicks="+memory.fluids.count()+
                " maxActive="+memory.maxActive.get()+" convergencePasses="+memory.convergencePasses+" sharedClipUnchanged=true sharedBoundsUnchanged=true differences="+count+" detail="+differences);
        h.assertTrue(count==0,"Concurrent memory slice mismatch "+rotation+" round="+round+" count="+count+": "+differences.stream().limit(3).toList());
        h.assertTrue(memory.fluids.count()>0,"Deferred convergence did not exercise fluid scheduling");
    }
    private static int tickDifferences(String label,Map<Long,List<TickImage>> expected,Map<Long,List<TickImage>> actual,List<String> differences){
        int count=0;var chunks=new HashSet<>(expected.keySet());chunks.addAll(actual.keySet());
        for(long chunk:chunks)if(!Objects.equals(expected.get(chunk),actual.get(chunk))){
            if(differences.size()<12)differences.add(label+" chunk="+new ChunkPos(chunk)+" serial="+expected.get(chunk)+" parallel="+actual.get(chunk));count++;
        }return count;
    }
}
