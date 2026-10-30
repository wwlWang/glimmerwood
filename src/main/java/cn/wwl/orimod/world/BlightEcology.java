package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.config.OriServerConfig;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** A single server scheduler for loaded growth and resumable restoration in every dimension. */
public final class BlightEcology {
    private record Key(ServerLevel level,BlockPos center){Key{center=center.immutable();}}
    private record ChunkKey(ServerLevel level,long chunk){}
    private static final Map<Key,Boolean> NOTICES=new ConcurrentHashMap<>();
    private static final Map<ServerLevel,BlightData> DATA=new IdentityHashMap<>();
    private static final Map<Key,Work> WORK=new HashMap<>();
    private static final Map<ChunkKey,Set<Key>> CHUNKS=new HashMap<>();
    private static final ArrayDeque<Key> GROW=new ArrayDeque<>(),RESTORE=new ArrayDeque<>();
    private static final Set<Key> GROW_QUEUED=new HashSet<>(),RESTORE_QUEUED=new HashSet<>();
    private static final Direction[] DIRS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static long clock;
    private static int lastReads,lastWrites,lastIndices,lastCandidates,maxReads,maxWrites,workVisits;
    private static final class Node {final BlockPos floor;int mask=15;Node(BlockPos floor){this.floor=floor;}}
    private record Retry(BlockPos from,int direction,long target,long due){}
    private static final class Work {
        final Key key;final BlightData data;final BlightData.Region region;
        final long[] seeds;int seedCursor;final Map<Long,BlockPos> floors=new LinkedHashMap<>();
        final List<Node> edge=new ArrayList<>();final Long2IntOpenHashMap edgeIndex=new Long2IntOpenHashMap();
        final LongOpenHashSet traversed=new LongOpenHashSet();final Long2LongOpenHashMap retry=new Long2LongOpenHashMap();
        final PriorityQueue<Retry> suspended=new PriorityQueue<>(Comparator.comparingLong(Retry::due));
        double tokens;long retryAt,leafRetryAt,plantRetryAt,lastFloor;int configuredRadius,lastDirection;
        Work(Key key,BlightData data,BlightData.Region r){this.key=key;this.data=data;region=r;
            seeds=r.chunks.values().stream().flatMapToLong(p->Arrays.stream(p.positions)).toArray();edgeIndex.defaultReturnValue(-1);}
        void add(BlockPos floor){long c=BlightRegion.column(floor);if(!traversed.add(c)||traversed.size()>BlightEcologyRules.MAX_COLUMNS)return;edgeIndex.put(c,edge.size());edge.add(new Node(floor));}
        void remove(int index){var last=edge.removeLast();if(index<edge.size()){edge.set(index,last);edgeIndex.put(BlightRegion.column(last.floor),index);}}
        void consume(int index,int direction){var n=edge.get(index);n.mask&=~(1<<direction);if(n.mask==0){edgeIndex.remove(BlightRegion.column(n.floor));remove(index);}}
        void frontier(){edge.clear();edgeIndex.clear();traversed.clear();retry.clear();suspended.clear();for(var p:region.ecology.columns())add(p);configuredRadius=OriServerConfig.blightRadius();}
        void defer(int index,int direction,long target){
            var from=edge.get(index).floor;consume(index,direction);
            if(!retry.containsKey(target)){retry.put(target,clock+40);suspended.add(new Retry(from,direction,target,clock+40));}
        }
        void resume(BlightEcologyRules.Budget b){
            while(!suspended.isEmpty()&&suspended.peek().due<=clock){
                if(!b.index())return;var next=suspended.remove();retry.remove(next.target);int i=edgeIndex.get(BlightRegion.column(next.from));
                if(i>=0)edge.get(i).mask|=1<<next.direction;
                else {var n=new Node(next.from);n.mask=1<<next.direction;edgeIndex.put(BlightRegion.column(next.from),edge.size());edge.add(n);}
            }
        }
        void recheck(BlockPos target,BlightEcologyRules.Budget b){
            for(int direction=0;direction<4;direction++){
                b.index();var from=target.relative(DIRS[direction].getOpposite());long key=BlightRegion.column(from);
                if(!traversed.contains(key))continue;
                var owner=data.columnOwner(from);var journal=owner.isPresent()?data.ecology(owner.get()):null;var floor=journal==null?null:journal.floor(from);
                if(floor==null)continue;int index=edgeIndex.get(key);
                if(index>=0)edge.get(index).mask|=1<<direction;
                else{var node=new Node(floor);node.mask=1<<direction;edgeIndex.put(key,edge.size());edge.add(node);}
            }
        }
    }
    public static void wake(ServerLevel level,BlockPos center){NOTICES.put(new Key(level,center),true);}
    public static void unload(ServerLevel level,BlockPos center){NOTICES.put(new Key(level,center),false);}
    public static void unloadChunk(ChunkEvent.Unload event){
        if(event.getLevel() instanceof ServerLevel level){
            var keys=CHUNKS.get(new ChunkKey(level,event.getChunk().getPos().toLong()));if(keys!=null)for(var k:List.copyOf(keys))NOTICES.put(k,false);
        }
    }
    private static void enqueue(Key key,boolean wave){var set=wave?RESTORE_QUEUED:GROW_QUEUED;if(set.add(key))(wave?RESTORE:GROW).add(key);}
    private static void forget(Key key){
        WORK.remove(key);GROW_QUEUED.remove(key);RESTORE_QUEUED.remove(key);GROW.remove(key);RESTORE.remove(key);
        var chunk=new ChunkKey(key.level,new ChunkPos(key.center).toLong());var set=CHUNKS.get(chunk);if(set!=null){set.remove(key);if(set.isEmpty())CHUNKS.remove(chunk);}
    }
    private static void attach(MinecraftServer server){
        for(var level:server.getAllLevels()){
            var data=BlightData.get(level);if(DATA.put(level,data)==data)continue;
            for(var key:List.copyOf(WORK.keySet()))if(key.level==level)forget(key);
            for(var r:data.regions())if(r.purifying||level.getChunkSource().getChunkNow(r.center.getX()>>4,r.center.getZ()>>4)!=null)wake(level,r.center);
        }
        int count=0;for(var e:NOTICES.entrySet()){
            if(count++==64)break;var key=e.getKey();if(key.level.getServer()!=server)continue;NOTICES.remove(key,e.getValue());
            var data=DATA.get(key.level);if(data==null)continue;var r=data.region(key.center);
            if(r==null){forget(key);RESTORE_QUEUED.remove(key);continue;}
            if(!e.getValue()&&!r.purifying){forget(key);continue;}
            var work=WORK.get(key);if(work==null||work.region!=r){work=new Work(key,data,r);WORK.put(key,work);CHUNKS.computeIfAbsent(new ChunkKey(key.level,new ChunkPos(key.center).toLong()),k->new HashSet<>()).add(key);}
            enqueue(key,r.purifying);
        }
    }
    public static void tick(ServerTickEvent.Post event){step(event.getServer(),true);}
    public static void step(MinecraftServer server,boolean softLimit){
        attach(server);clock++;workVisits=0;long start=System.nanoTime();var b=new BlightEcologyRules.Budget(512,OriServerConfig.blightWrites());
        for(var d:DATA.values()){d.lastReads=0;d.lastWrites=0;}
        boolean growing=OriServerConfig.blightEnabled()&&!GROW_QUEUED.isEmpty();
        // A full protection scan occasionally goes first, so restoration cannot starve its 441-read reservation.
        boolean growthFirst=growing&&clock%8==0;
        if(growthFirst)drain(false,b,start,softLimit,8);
        drain(true,b,start,softLimit,growing&&!growthFirst?Math.min(24,OriServerConfig.blightWrites()):32);
        if(growing&&!growthFirst)drain(false,b,start,softLimit,8);
        lastReads=b.reads();lastWrites=b.writes();lastIndices=b.indices();lastCandidates=b.candidates();maxReads=Math.max(maxReads,lastReads);maxWrites=Math.max(maxWrites,lastWrites);
    }
    private static void drain(boolean wave,BlightEcologyRules.Budget b,long start,boolean softLimit,int writeAllowance){
        var queue=wave?RESTORE:GROW;var set=wave?RESTORE_QUEUED:GROW_QUEUED;int visits=Math.min(32,queue.size()),startWrites=b.writes();
        for(int i=0;i<visits;i++){
            if(workVisits>=32||b.remainingIndices()==0||b.remainingReads()==0||b.remainingWrites()==0||b.writes()-startWrites>=writeAllowance||softLimit&&System.nanoTime()-start>=1_000_000)return;
            b.index();workVisits++;var key=queue.remove();if(!set.remove(key))continue;var w=WORK.get(key);
            if(w==null)continue;var r=w.data.region(key.center);if(r!=w.region){forget(key);continue;}
            if(r.purifying!=wave){enqueue(key,r.purifying);continue;}
            int reads=b.reads(),writes=b.writes();
            if(wave)w.data.restore(key.level,r,b,writeAllowance-(b.writes()-startWrites));
            else grow(w,b,Math.min(8,writeAllowance-(b.writes()-startWrites)));
            w.data.lastReads+=b.reads()-reads;w.data.lastWrites+=b.writes()-writes;
            if(w.data.region(key.center)!=null&&WORK.containsKey(key))enqueue(key,wave);else forget(key);
        }
    }
    private static boolean loaded(ServerLevel level,BlockPos p){return level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null;}
    private static void grow(Work w,BlightEcologyRules.Budget b,int writes){
        var level=w.key.level;var center=w.key.center;var r=w.region;
        if(!loaded(level,center)){forget(w.key);return;}
        if(clock<w.retryAt||!b.read())return;
        if(!level.getBlockState(center).is(OriBlocks.GLOOM_CORE)){w.data.removed(level,center);forget(w.key);return;}
        if(!r.naturalEcology&&!OriServerConfig.legacyCoresSpread()&&r.ecology==null)return;
        if(r.ecology==null){
            while(w.seedCursor<w.seeds.length){
                var p=BlockPos.of(w.seeds[w.seedCursor]);if(!loaded(level,p)){w.retryAt=clock+40;return;}
                if(!b.read())return;var s=level.getBlockState(p);w.seedCursor++;
                if(s.is(OriBlocks.BLIGHTED_SOIL)||s.is(OriBlocks.GLOOM_WATER))w.floors.merge(BlightRegion.column(p),p,(a,c)->a.getY()>=c.getY()?a:c);
            }
            if(!w.data.initializeEcology(center,w.floors.values(),OriServerConfig.legacyCoresSpread()))return;w.frontier();
        }
        var journal=r.ecology;if(journal.readOnly())return;
        journal.loadedTick();w.data.setDirty();
        for(int i=0;i<4&&b.remainingIndices()>=5;i++){
            b.index();var expired=journal.expireProtection();if(expired==null)break;w.recheck(expired,b);
        }
        BlightMobs.tick(level,r,b);
        if(!OriServerConfig.blightSpreads())w.tokens=0;
        if(OriServerConfig.blightSpreads()&&journal.loadedTicks()%20==0)w.tokens=BlightEcologyRules.credit(w.tokens,OriServerConfig.blightMultiplier());
        if(w.configuredRadius!=OriServerConfig.blightRadius())w.frontier();
        w.resume(b);
        int totalRecords=DATA.values().stream().mapToInt(BlightData::ecologyRecords).sum();
        if(totalRecords>=262144||journal.size()>=BlightEcologyRules.MAX_RECORDS)return;
        int before=b.writes(),grown=0;
        while(w.tokens>=1&&!w.edge.isEmpty()&&grown<2&&b.writes()-before<writes&&b.remainingReads()>=32&&b.remainingIndices()>0&&b.candidate()){
            long random=mix(center.asLong()+journal.nextRandom());int index=Math.floorMod(random,w.edge.size());
            boolean extend=(random&3)==0&&w.edgeIndex.get(w.lastFloor)>=0;if(extend)index=w.edgeIndex.get(w.lastFloor);var node=w.edge.get(index);
            int direction=extend?w.lastDirection:(int)(random>>>32)&3;while((node.mask&(1<<direction))==0)direction=(direction+1)&3;
            var next=node.floor.relative(DIRS[direction]);long column=BlightRegion.column(next);
            if(w.retry.containsKey(column)){w.consume(index,direction);continue;}
            if(!BlightEcologyRules.within(next.getX()-center.getX(),next.getZ()-center.getZ(),OriServerConfig.blightRadius())||journal.protectedAt(next)||w.traversed.contains(column)){
                w.consume(index,direction);continue;
            }
            var owner=w.data.columnOwner(next);
            if(owner.isPresent()){
                var other=w.data.ecology(owner.get());var floor=other==null?null:other.floor(next);w.consume(index,direction);
                if(floor!=null&&!w.data.ecologyProtects(floor))w.add(floor);
                continue;
            }
            if(!loaded(level,next)){w.defer(index,direction,column);continue;}
            BlockPos floor=null;
            for(int dy=4;dy>=-4;dy--){if(!b.read())return;var p=next.offset(0,dy,0);if(BlightProtection.soil(level.getBlockState(p))){floor=p;break;}}
            if(floor==null){w.consume(index,direction);continue;}
            var protection=BlightProtection.inspect(level,floor,center,b);
            if(protection.result()==BlightProtection.Result.DEFER){w.defer(index,direction,column);continue;}
            if(protection.result()==BlightProtection.Result.PROTECTED){w.data.protectColumn(center,floor,protection.reason());w.consume(index,direction);continue;}
            if(b.remainingReads()<8)return;
            var batch=BlightConversion.ground(level,floor,b,random);
            if(batch.isEmpty()){w.consume(index,direction);continue;}
            if(batch.size()>writes-(b.writes()-before)||batch.size()>b.remainingWrites())return;
            if(totalRecords+batch.size()>262144)return;
            if(!BlightConversion.commit(level,w.data,center,floor,batch,false,b)){w.defer(index,direction,column);continue;}
            totalRecords+=batch.size();w.tokens--;grown++;w.consume(index,direction);w.add(floor);w.lastFloor=column;w.lastDirection=direction;
        }
        // A bounded rotating pass includes legacy columns and bone-meal/re-grown vegetation.
        for(int scan=0;scan<4&&clock>=w.plantRetryAt&&journal.plantFloor()!=null&&b.remainingReads()>=16&&b.remainingIndices()>0&&b.writes()-before<writes;scan++){
            var floor=journal.plantFloor();
            if(journal.protectedAt(floor)||!loaded(level,floor)){journal.nextPlant();continue;}
            var batch=BlightConversion.plants(level,floor,journal,b);
            if(batch.isEmpty()){journal.nextPlant();continue;}
            int misses=b.chunkMisses();var check=BlightProtection.inspect(level,floor,center,b);
            if(check.result()==BlightProtection.Result.DEFER){w.plantRetryAt=clock+(b.chunkMisses()>misses?40:1);break;}
            if(check.result()==BlightProtection.Result.PROTECTED)w.data.protectColumn(center,floor,check.reason());
            if(check.result()==BlightProtection.Result.ALLOW){
                if(batch.size()>writes-(b.writes()-before)||totalRecords+batch.size()>262144)break;
                if(!BlightConversion.commit(level,w.data,center,floor,batch,true,b))break;
                totalRecords+=batch.size();
            }
            journal.nextPlant();
        }
        // Decorations cannot delay ground conversion and carry their own protection check at the actual write height.
        for(int scanned=0;scanned<4&&journal.decorationFloor()!=null&&clock>=w.leafRetryAt&&b.remainingReads()>=32&&b.remainingIndices()>0&&b.writes()-before<writes;scanned++){
            var floor=journal.decorationFloor();if(!loaded(level,floor)){w.leafRetryAt=clock+40;return;}
            var p=floor.above(journal.decorationY());if(!b.index())return;
            var chunk=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);
            if(chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,p.getX()&15,p.getZ()&15)<p.getY()){journal.finishDecoration();continue;}
            if(!b.read())return;var state=level.getBlockState(p);
            if(BlightConversion.leaf(state)){
                int misses=b.chunkMisses();var protection=BlightProtection.inspect(level,p,center,b);
                if(protection.result()==BlightProtection.Result.DEFER){w.leafRetryAt=clock+(b.chunkMisses()>misses?40:1);return;}
                if(protection.result()==BlightProtection.Result.ALLOW){
                    var change=new BlightRegion.Change(p,state,OriBlocks.BLIGHT_LEAVES.get().defaultBlockState());
                    if(totalRecords<262144&&!BlightConversion.commit(level,w.data,center,floor,List.of(change),true,b))return;
                }
            }
            journal.nextDecoration(state.isAir());
        }
    }
    private static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    public static Map<String,Object> metrics(){return Map.of("reads",lastReads,"writes",lastWrites,"indices",lastIndices,"candidates",lastCandidates,"maxReads",maxReads,"maxWrites",maxWrites,"loadedWork",WORK.size());}
    public static void resetMetrics(){maxReads=maxWrites=0;}
    public static void clear(){NOTICES.clear();DATA.clear();WORK.clear();CHUNKS.clear();GROW.clear();RESTORE.clear();GROW_QUEUED.clear();RESTORE_QUEUED.clear();clock=0;lastReads=lastWrites=lastIndices=lastCandidates=maxReads=maxWrites=0;}
    private BlightEcology(){}
}
