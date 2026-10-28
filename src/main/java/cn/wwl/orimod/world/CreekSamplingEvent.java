package cn.wwl.orimod.world;

/** Optional JFR observation. No counters, logging or event allocation when disabled. */
@jdk.jfr.Name("orimod.CreekTerrainSampling")
@jdk.jfr.Label("Creek immutable terrain snapshot")
@jdk.jfr.StackTrace(false)
public final class CreekSamplingEvent extends jdk.jfr.Event {
    private static final jdk.jfr.EventType TYPE=jdk.jfr.EventType.getEventType(CreekSamplingEvent.class);
    public String cause;
    public int calls;
    public int reused;
    public int gridCorners;
    public int gridStep;
    public int originX,originZ;
    public long cpuNanos;
    static CreekSamplingEvent begin(String cause){
        if(!TYPE.isEnabled())return null;
        var e=new CreekSamplingEvent();e.cause=cause;e.gridStep=CreekHeightGrid.STEP;e.begin();return e;
    }
}
