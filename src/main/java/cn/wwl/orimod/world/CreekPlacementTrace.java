package cn.wwl.orimod.world;

/** A handful of phase boundaries, enabled only by the existing developer switch. */
public final class CreekPlacementTrace {
    private static final boolean ENABLED=Boolean.getBoolean("orimod.profileRuins");
    private static final ThreadLocal<Trace> ACTIVE=new ThreadLocal<>();
    public static void prepareDiagnostics(){
        if(!ENABLED)return;
        jdk.jfr.EventType.getEventType(Trace.class);
        Allocation.BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }
    public static Trace begin(int x,int z){
        if(!ENABLED)return null;
        var t=new Trace();t.chunkX=x;t.chunkZ=z;t.begin();t.start=System.nanoTime();
        t.bytesBefore=Allocation.BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId());ACTIVE.set(t);return t;
    }
    public static void mark(int stage){
        if(!ENABLED)return;var t=ACTIVE.get();if(t==null)return;
        long now=System.nanoTime();
        switch(stage){case 0->t.processStart=now;case 1->t.processEnd=now;case 2->t.writeEnd=now;case 3->t.edgeEnd=now;}
    }
    public static void finish(Trace t){
        if(t==null)return;
        long now=System.nanoTime();t.end();ACTIVE.remove();
        t.selectMs=(t.processStart-t.start)/1e6;
        t.processMs=(t.processEnd-t.processStart)/1e6;
        t.writeMs=((t.writeEnd==0?now:t.writeEnd)-t.processEnd)/1e6;
        t.edgeMs=t.edgeEnd==0?0:(t.edgeEnd-t.writeEnd)/1e6;
        t.finalizeMs=t.edgeEnd==0?0:(now-t.edgeEnd)/1e6;
        t.allocatedBytes=Allocation.BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId())-t.bytesBefore;t.commit();
    }
    private static final class Allocation { static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean(); }
    @jdk.jfr.Name("orimod.CreekTemplate") @jdk.jfr.StackTrace(false)
    public static final class Trace extends jdk.jfr.Event {
        public int chunkX,chunkZ;
        public double selectMs,processMs,writeMs,edgeMs,finalizeMs;
        public long allocatedBytes;
        private transient long start,processStart,processEnd,writeEnd,edgeEnd,bytesBefore;
    }
    private CreekPlacementTrace(){}
}
