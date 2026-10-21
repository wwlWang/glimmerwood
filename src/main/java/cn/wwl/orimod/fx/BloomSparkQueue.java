package cn.wwl.orimod.fx;

import java.util.*;

/** Bounded, one-shot client transition queue. No replay on chunk initialization or world changes. */
public final class BloomSparkQueue {
    public static final int CAPACITY=2048, MAX_AGE=160;
    private final LinkedHashMap<Long,Long> pending=new LinkedHashMap<>();
    public void offer(long pos,long tick){
        if(pending.containsKey(pos))return;
        if(pending.size()>=CAPACITY)pending.remove(pending.keySet().iterator().next());
        pending.put(pos,tick);
    }
    public List<Long> drain(long now,int budget){
        var result=new ArrayList<Long>();var it=pending.entrySet().iterator();
        while(it.hasNext()){
            var e=it.next();
            if(now-e.getValue()>MAX_AGE){it.remove();continue;}
            if(result.size()>=Math.min(FxBudget.MAX_PER_BURST,Math.max(0,budget)))break;
            result.add(e.getKey());it.remove();
        }
        return result;
    }
    public int size(){return pending.size();}
    public void clear(){pending.clear();}
}
