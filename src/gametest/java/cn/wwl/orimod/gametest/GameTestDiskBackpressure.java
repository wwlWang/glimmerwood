package cn.wwl.orimod.gametest;

import java.time.Instant;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.IOWorker;

/** Test-only disk barrier; boundary drains also advance normal chunk unloading,
 * without ticking entities/gameplay, loading new chunks or forcing active chunks out. */
public final class GameTestDiskBackpressure {
    public static final int MAX_SUBMISSIONS = 512;
    public interface Metrics {
        int orimod$pendingWrites();
        int orimod$peakPendingWrites();
        int orimod$takeWindowPeak();
    }
    // All accesses are on the server thread. No world/chunk payloads are retained.
    private static final Map<IOWorker,Integer> SUBMITTED = new IdentityHashMap<>();
    private record Deferred(String phase,String log,boolean unload) {}
    private static final Map<ServerLevel,Deferred> DEFERRED = new IdentityHashMap<>();
    private static final ArrayDeque<String> TIMERS = new ArrayDeque<>();
    private static String batch = "startup", test = "outside-test";
    private static long waitNanos;
    private static final Map<ServerLevel,Boolean> UNLOADING = new IdentityHashMap<>();
    private static final Field TO_DROP=field("toDrop"), PENDING_UNLOADS=field("pendingUnloads"),
            UNLOAD_QUEUE=field("unloadQueue"), MODIFIED=field("modified"), VISIBLE=field("visibleChunkMap");

    private GameTestDiskBackpressure() {}

    public static void batch(String name) {
        batch=name;
        observerCounts(ForestLavaEvidence.class,ForestLavaEvidence.ENABLED,"EVENTS","SNAPSHOTS","STACKS","SECTION_ORIGINS");
        observerCounts(ForestWaterEvidence.class,ForestWaterEvidence.ENABLED,"WRITES","PHASES","OWN_WRITES","SLICES");
        observerCounts(ForestStructureEvidence.class,ForestStructureEvidence.ENABLED,"WRITES","STACKS","COVERAGE_ERRORS","SECTION_ORIGINS");
        observerCounts(CreekGuardEvidence.class,CreekGuardEvidence.ENABLED,"EVENTS","PHASES");
    }
    /** Read-only test instrumentation; no observer collection is cleared or enabled here. */
    private static void observerCounts(Class<?> type,boolean enabled,String... fields) {
        var row=new StringBuilder("GAMETEST_OBSERVER_RETENTION timestamp=").append(Instant.now())
                .append(" batch=").append(batch).append(" observer=").append(type.getSimpleName()).append(" enabled=").append(enabled);
        for(String name:fields)try{
            var field=type.getDeclaredField(name);field.setAccessible(true);Object value=field.get(null);
            int size=value instanceof Map<?,?> m?m.size():((java.util.Collection<?>)value).size();
            row.append(' ').append(name).append('=').append(size);
            if(!enabled&&size!=0)throw new IllegalStateException("Disabled observer retains data: "+type.getSimpleName()+"."+name+"="+size);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot inspect observer retention",e);}
        System.out.println(row);
    }
    public static String test(String name) { String previous = test; test = name; return previous; }
    public static long waitNanos() { return waitNanos; }

    /** Construct BEFORE starting the clock; close AFTER recording the elapsed time. */
    public static Scope measured(ServerLevel level, String name) {
        checkThread(level);
        TIMERS.push(name);
        return new Scope(level, name);
    }

    public static final class Scope implements AutoCloseable {
        private final ServerLevel level;
        private final String name;
        private boolean closed;
        private Scope(ServerLevel level,String name) { this.level=level; this.name=name; }
        public void close() {
            checkThread(level);
            if (closed) return;
            if (!name.equals(TIMERS.peek())) throw new IllegalStateException("Unbalanced timing scope " + name);
            TIMERS.pop(); closed=true;
            if (TIMERS.isEmpty() && !DEFERRED.isEmpty()) {
                var pending = new IdentityHashMap<>(DEFERRED); DEFERRED.clear();
                pending.forEach((l,reason)->{
                    System.out.println(reason.log()); // Logging is also outside the measured interval.
                    if(reason.unload()) unloadAndDrain(l,"after-measurement-"+name+"-"+reason.phase());
                    else drain(l,"after-measurement-"+name+"-"+reason.phase());
                });
            }
        }
    }

    /** Called after each successful ChunkMap.save submission, including late unload/save-all. */
    public static void submitted(ServerLevel level) {
        checkThread(level);
        var worker=worker(level);
        if (!(worker instanceof Metrics metrics)) throw new IllegalStateException("Submission limiter without queue metrics");
        int submissions=SUBMITTED.merge(worker,1,Integer::sum);
        // Pending size is published by the I/O thread. Also bound foreground commands
        // not consumed by that thread yet; otherwise its snapshot can lag a whole burst.
        if (metrics.orimod$pendingWrites()>MAX_SUBMISSIONS || submissions>=MAX_SUBMISSIONS)
            drain(level,"submission-limit");
    }

    static void preparedChunk(ServerLevel level,int completed,String phase) {
        if (completed%16==0) drain(level,phase+"-"+completed);
    }

    public static void drain(ServerLevel level,String phase) {
        checkThread(level);
        var worker=worker(level);
        Metrics metrics=worker instanceof Metrics m ? m : null;
        int before=metrics==null?-1:metrics.orimod$pendingWrites();
        if (defer(level,phase,false)) return;
        String timestamp=Instant.now().toString();
        long start=System.nanoTime();
        try {
            worker.synchronize(true).get(120,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted disk checkpoint "+phase,e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed disk checkpoint "+phase,e);
        }
        long elapsed=System.nanoTime()-start; waitNanos+=elapsed;
        int submissions=SUBMITTED.getOrDefault(worker,0); SUBMITTED.remove(worker);
        int after=metrics==null?-1:metrics.orimod$pendingWrites();
        int window=metrics==null?-1:metrics.orimod$takeWindowPeak();
        int peak=metrics==null?-1:metrics.orimod$peakPendingWrites();
        System.out.println("GAMETEST_DISK_BACKPRESSURE timestamp="+timestamp+" batch="+batch+" test="+test
                +" measured=false intervals=none dimension="+level.dimension().location()+" phase="+phase
                +" submissions="+submissions+" before="+before+" after="+after+" windowPeak="+window
                +" totalPeak="+peak+" waitMs="+elapsed/1e6);
        if (after>0) throw new IllegalStateException("Writes continued past disk checkpoint "+phase+": "+after);
    }

    /** Boundary only: releasing tickets does not itself run processUnloads. Service
     * its callbacks, publish the new visible map, then flush the NBT they submitted.
     * Submission-level disk waits remain bounded at 512 during this operation. */
    public static void unloadAndDrain(ServerLevel level,String phase) {
        checkThread(level);
        if(defer(level,phase,true)) return;
        if(UNLOADING.putIfAbsent(level,true)!=null)
            throw new IllegalStateException("Reentrant unload checkpoint "+phase);
        String timestamp=Instant.now().toString();
        var source=level.getChunkSource();
        var before=unloadState(source.chunkMap);
        long start=System.nanoTime(), ioBefore=waitNanos;
        int passes=0,stable=0,mailboxWaits=0;
        boolean timedOut=false;
        try {
            while(stable<2) {
                if(System.nanoTime()-start>=60_000_000_000L){timedOut=true;break;}
                // false skips tickChunks (random/entity/gameplay ticks). The normal
                // ticket/unload machinery still runs. pollTask publishes changes
                // made by processUnloads to visibleChunkMap via promoteChunkMap.
                // scheduleUnload may requeue an unfinished holder immediately.
                // Always return to pollTask, which can finish its main-thread
                // continuations; an unlimited supplier can spin forever here.
                boolean work=source.pollTask();
                if(hasUnreadyHolder(source.chunkMap)) {
                    // Do not repeatedly requeue the same unfinished save future.
                    // Let main-thread continuations progress before trying again.
                    mailboxWaits++;
                } else {
                    long sliceDeadline=Math.min(start+60_000_000_000L,System.nanoTime()+5_000_000L);
                    source.tick(()->System.nanoTime()<sliceDeadline,false);
                    work=source.pollTask()||work;
                }
                passes++;
                var state=unloadState(source.chunkMap);
                stable=state.empty()&&!work?stable+1:0;
                if(!work&&stable<2) LockSupport.parkNanos(100_000);
            }
            // This is an auxiliary memory checkpoint, not a gameplay assertion.
            // Submission backpressure remains active even if unloading times out.
            if(!timedOut) drain(level,phase);
            long elapsed=System.nanoTime()-start,io=waitNanos-ioBefore;
            var after=unloadState(source.chunkMap);
            System.out.println("GAMETEST_UNLOAD_BACKPRESSURE timestamp="+timestamp+" batch="+batch+" test="+test
                    +" measured=false intervals=none phase="+phase+" passes="+passes+" mailboxWaits="+mailboxWaits
                    +" completed="+after.empty()+" timeout="+timedOut
                    +" beforeDrop="+before.drop()+" beforePending="+before.pending()+" beforeQueue="+before.queue()
                    +" beforeVisible="+before.visible()+" afterDrop="+after.drop()+" afterPending="+after.pending()
                    +" afterQueue="+after.queue()+" afterModified="+after.modified()+" afterVisible="+after.visible()
                    +" elapsedMs="+elapsed/1e6+" nestedIoMs="+io/1e6+" nonIoMs="+(elapsed-io)/1e6);
            if(timedOut) System.out.println("GAMETEST_UNLOAD_TIMEOUT timestamp="+Instant.now()+" batch="+batch+" test="+test
                    +" phase="+phase+" state="+after+" holders="+pendingHolderStates(source.chunkMap));
        } finally { UNLOADING.remove(level); }
    }

    private static boolean hasUnreadyHolder(ChunkMap map) {
        try {
            for(Object v:((Map<?,?>)PENDING_UNLOADS.get(map)).values())
                if(!((ChunkHolder)v).isReadyForSaving())return true;
            return false;
        } catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    private static java.util.List<String> pendingHolderStates(ChunkMap map) {
        try {
            var out=new java.util.ArrayList<String>();
            for(Object v:((Map<?,?>)PENDING_UNLOADS.get(map)).values()) {
                var h=(ChunkHolder)v;var future=h.getSaveSyncFuture();
                out.add(h.getPos()+":refs="+h.getGenerationRefCount()+",ready="+h.isReadyForSaving()
                        +",saveDone="+future.isDone()+",saveExceptional="+future.isCompletedExceptionally());
            }
            return out;
        } catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }

    private static boolean defer(ServerLevel level,String phase,boolean unload) {
        if(TIMERS.isEmpty()) return false;
        var old=DEFERRED.get(level);
        if(old==null) {
            var worker=worker(level);
            int pending=worker instanceof Metrics m?m.orimod$pendingWrites():-1;
            DEFERRED.put(level,new Deferred(phase,"GAMETEST_DISK_DEFERRED timestamp="+Instant.now()+" batch="+batch+" test="+test
                    +" phase="+phase+" pending="+pending+" submissions="+SUBMITTED.getOrDefault(worker,0)
                    +" measured=true intervals="+String.join(",",TIMERS),unload));
        } else if(unload&&!old.unload()) DEFERRED.put(level,new Deferred(old.phase(),old.log(),true));
        return true;
    }
    private record UnloadState(int drop,int pending,int queue,boolean modified,int visible) {
        boolean empty(){return drop==0&&pending==0&&queue==0&&!modified;}
    }
    private static UnloadState unloadState(ChunkMap map) {
        try {
            return new UnloadState(((java.util.Set<?>)TO_DROP.get(map)).size(),((Map<?,?>)PENDING_UNLOADS.get(map)).size(),
                    ((java.util.Queue<?>)UNLOAD_QUEUE.get(map)).size(),MODIFIED.getBoolean(map),((Map<?,?>)VISIBLE.get(map)).size());
        } catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    private static Field field(String name) {
        try {var f=ChunkMap.class.getDeclaredField(name);f.setAccessible(true);return f;}
        catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }

    private static IOWorker worker(ServerLevel level) {
        return (IOWorker)level.getChunkSource().chunkMap.chunkScanner();
    }
    private static void checkThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Disk checkpoint/scope off server thread");
    }
}
