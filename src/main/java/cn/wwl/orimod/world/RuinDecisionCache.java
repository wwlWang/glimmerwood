package cn.wwl.orimod.world;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Bounded single-flight memoization; expensive work never holds the map monitor. */
final class RuinDecisionCache<K,V> {
    private final int capacity;
    private final LinkedHashMap<K,CompletableFuture<V>> entries=new LinkedHashMap<>(16,.75f,true);
    RuinDecisionCache(int capacity){if(capacity<1)throw new IllegalArgumentException("Positive capacity required");this.capacity=capacity;}
    V get(K key,Supplier<V> factory){
        CompletableFuture<V> value;boolean owner=false;
        synchronized(entries){
            value=entries.get(key);
            if(value==null){
                var it=entries.entrySet().iterator();
                while(entries.size()>=capacity&&it.hasNext())if(it.next().getValue().isDone())it.remove();
                // Never evict an in-flight computation or wait while holding the
                // monitor. At saturation an independent uncached call is bounded.
                if(entries.size()<capacity){value=new CompletableFuture<>();entries.put(key,value);owner=true;}
            }
        }
        if(value==null)return factory.get();
        if(owner){
            try{value.complete(factory.get());}
            catch(Throwable failure){value.completeExceptionally(failure);synchronized(entries){entries.remove(key,value);}}
        }
        return value.join();
    }
    V completed(K key){
        synchronized(entries){var value=entries.get(key);return value!=null&&value.isDone()&&!value.isCompletedExceptionally()?value.join():null;}
    }
    void remember(K key,V value){
        synchronized(entries){
            if(entries.containsKey(key))return;
            var it=entries.entrySet().iterator();
            while(entries.size()>=capacity&&it.hasNext())if(it.next().getValue().isDone())it.remove();
            if(entries.size()<capacity)entries.put(key,CompletableFuture.completedFuture(value));
        }
    }
    int size(){synchronized(entries){return entries.size();}}
}
