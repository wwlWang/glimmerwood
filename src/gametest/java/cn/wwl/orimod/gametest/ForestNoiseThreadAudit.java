package cn.wwl.orimod.gametest;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only counts during the CPU audit, with no logging or stack capture. */
public final class ForestNoiseThreadAudit {
    private static volatile boolean active;
    private static final Map<String,long[]> COUNTS=new ConcurrentHashMap<>();
    public static void record(int type){
        if(active)COUNTS.computeIfAbsent(Thread.currentThread().getName(),k->new long[3])[type]++;
    }
    static void begin(){COUNTS.clear();active=true;}
    static Map<String,Object> finish(){
        active=false;var result=new TreeMap<String,Object>();
        COUNTS.forEach((thread,c)->result.put(thread,Map.of("baseHeight",c[0],"baseColumn",c[1],"noiseCells",c[2])));
        return result;
    }
    private ForestNoiseThreadAudit(){}
}
