package cn.wwl.orimod.world;

/** Optional candidate diagnostics. No logging or allocation on the normal path. */
public final class RuinSitingTrace {
    private static final boolean ENABLED=Boolean.getBoolean("orimod.profileRuinSiting");
    public static Sample begin(RuinKind kind,int version,int x,int z){
        if(!ENABLED)return null;
        var s=new Sample();s.kind=kind.id;s.version=version;s.chunkX=x;s.chunkZ=z;s.begin();return s;
    }
    public static void end(Sample s,String reason){if(s!=null){s.reason=reason;s.end();s.commit();}}
    @jdk.jfr.Name("orimod.RuinSiting") @jdk.jfr.StackTrace(false)
    public static final class Sample extends jdk.jfr.Event {
        public String kind,reason;
        public int version,chunkX,chunkZ,heightCalls,columnCalls,partialColumnCalls,noiseCells;
    }
    private RuinSitingTrace(){}
}
