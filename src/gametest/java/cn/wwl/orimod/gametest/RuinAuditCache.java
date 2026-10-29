package cn.wwl.orimod.gametest;

import java.lang.reflect.*;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/** Test-only reflection adapter: reuse production bounded cache/pruning without split module packages. */
final class RuinAuditCache<K,V> {
    private static final Constructor<?> CTOR;
    private static final Method GET,COMPLETED,REMEMBER,SIZE,WINNER;
    static {try{
        var c=Class.forName("cn.wwl.orimod.world.RuinDecisionCache");CTOR=c.getDeclaredConstructor(int.class);CTOR.setAccessible(true);
        GET=c.getDeclaredMethod("get",Object.class,Supplier.class);COMPLETED=c.getDeclaredMethod("completed",Object.class);
        REMEMBER=c.getDeclaredMethod("remember",Object.class,Object.class);SIZE=c.getDeclaredMethod("size");
        WINNER=Class.forName("cn.wwl.orimod.world.RuinCandidateOrder").getDeclaredMethod("relevantWinner",boolean[].class,IntPredicate.class);
        for(var m:new Method[]{GET,COMPLETED,REMEMBER,SIZE,WINNER})m.setAccessible(true);
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private final Object cache;private final java.util.concurrent.atomic.LongAdder hits=new java.util.concurrent.atomic.LongAdder(),misses=new java.util.concurrent.atomic.LongAdder();
    RuinAuditCache(int capacity){try{cache=CTOR.newInstance(capacity);}catch(Exception e){throw new IllegalStateException(e);}}
    @SuppressWarnings("unchecked") V get(K key,Supplier<V> factory){
        V known=(V)call(COMPLETED,cache,key);if(known!=null){hits.increment();return known;}
        misses.increment();return (V)call(GET,cache,key,factory);
    }
    @SuppressWarnings("unchecked") V completed(K key){V known=(V)call(COMPLETED,cache,key);if(known==null)misses.increment();else hits.increment();return known;}
    void remember(K key,V value){call(REMEMBER,cache,key,value);}
    Map<String,Object> stats(){long h=hits.sum(),m=misses.sum();return Map.of("hits",h,"misses",m,"hitRate",h+m==0?0:(double)h/(h+m),"size",call(SIZE,cache));}
    static int relevantWinner(boolean[] relevant,IntPredicate viable){return (int)call(WINNER,null,relevant,viable);}
    private static Object call(Method method,Object receiver,Object... args){try{return method.invoke(receiver,args);}
        catch(InvocationTargetException e){throw new IllegalStateException(e.getCause());}catch(Exception e){throw new IllegalStateException(e);}}
}
